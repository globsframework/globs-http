package org.globsframework.http.server.apache;

import org.apache.commons.fileupload.MultipartStream;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.ReusableByteArrayOutputStream;
import org.globsframework.http.*;
import org.globsframework.http.model.HttpBodyData;
import org.globsframework.http.model.HttpGlobResponse;
import org.globsframework.http.model.StatusCode;
import org.globsframework.http.streams.MultiBufferOutputStream;
import org.globsframework.http.streams.MultiByteArrayInputStream;
import org.globsframework.json.GSonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultGlobHttpRequestHandler implements GlobHttpRequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.globsframework.http.DefaultGlobHttpRequestHandler");
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private final HttpOperation operation;
    private final Glob urlGlob;
    private final Glob paramType;
    private final HttpRequest request;
    private final EntityDetails requestEntityDetails;
    private final ResponseChannel responseChannel;
    private final HttpContext context;
    private final Glob header;
    private DataToSendProvider stream;
    private MultiByteArrayInputStream multiByteArrayInputStream;
    private long responseSize;
    private ByteBuffer currentResponseBuffer;

    public DefaultGlobHttpRequestHandler(HttpOperation operation, Glob urlGlob, Glob paramType, HttpRequest request,
                                         EntityDetails requestEntityDetails, ResponseChannel responseChannel, HttpContext context) {
        this.operation = operation;
        this.urlGlob = urlGlob;
        this.paramType = paramType;
        this.request = request;
        this.requestEntityDetails = requestEntityDetails;
        this.responseChannel = responseChannel;
        this.context = context;
        GlobType headerType = operation.getHeaderType();
        this.header = headerType != null ? parseHeader(headerType, request.getHeaders()) : null;
    }

    private Glob parseHeader(GlobType headerType, Header[] allHeaders) {
        MutableGlob instance = headerType.instantiate();
        for (Header allHeader : allHeaders) {
            final String name = allHeader.getName();
            final Field field = headerType.findField(name);
            if (field != null) {
                instance.set(field.asStringField(), allHeader.getValue());
            }
        }
        return instance;
    }

    public void callHandler() {
        operation.getExecutor().execute(() -> {
            callHandler(null);
        });
    }

    public void streamEnd(List<? extends Header> trailers) {
        if (multiByteArrayInputStream != null) {
            operation.getExecutor().execute(() -> {
                if (operation.getBodyType() != null) {
                    Glob glob = GSonUtils.decode(new InputStreamReader(multiByteArrayInputStream), operation.getBodyType());
                    callHandler(HttpInputData.fromGlob(glob));
                } else {
                    callHandler(HttpInputData.fromStream(multiByteArrayInputStream, requestEntityDetails.getContentLength()));
                }
            });
        } else {
            // already called in consumeRequest
        }
    }

    public void consumeRequest(ByteBuffer src) {
        if (multiByteArrayInputStream != null) {
            multiByteArrayInputStream.addBuffer(src);
        } else if (src.limit() - src.position() == requestEntityDetails.getContentLength()) {
            HttpInputData inputData;
            if (operation.getBodyType() != null) {
                Glob glob = null;
                try {
                    CharBuffer decode = UTF_8.decode(src);
                    glob = GSonUtils.decode(new Reader() {
                        public int read(char[] chars, int offset, int length) throws IOException {
                            int maxLen = Math.min(length, decode.remaining());
                            if (maxLen > 0) {
                                decode.get(chars, offset, maxLen);
                            }
                            return maxLen;
                        }

                        public void close() throws IOException {
                        }
                    }, operation.getBodyType());
                } catch (Exception e) {
                    send500(e);
                    return;
                }
                inputData = HttpInputData.fromGlob(glob);
            } else {
                int len = src.remaining();
                byte[] dst = new byte[len];
                src.get(dst);
                inputData = HttpInputData.fromStream(new ByteArrayInputStream(dst), len);
            }
            operation.getExecutor().execute(() -> {
                callHandler(inputData);
            });
        } else {
            multiByteArrayInputStream = new MultiByteArrayInputStream();
            multiByteArrayInputStream.addBuffer(src);
        }
    }

    private void callHandler(HttpInputData inputData) {
        try {
            operation.consume(inputData, urlGlob, paramType, header)
                    .whenComplete((httpOutputData, throwable) -> {
                        if (throwable != null) {
                            if (throwable instanceof CompletionException) {
                                manageException(throwable.getCause());
                            } else {
                                manageException(throwable);
                            }
                        } else if (httpOutputData != null) {
                            if (httpOutputData.isGlob()) {
                                Glob glob = httpOutputData.getGlob();
                                if (glob == null) {
                                    send204();
                                    return;
                                }
                                if (glob.getType() == GlobHttpContent.TYPE) {
                                    responseFromHttpContent(glob);
                                    return;
                                }
                                if (glob.getType().hasAnnotation(HttpGlobResponse.UNIQUE_KEY)) {
                                    responseCustomHttpContent(glob);
                                    return;
                                }
                                MultiBufferOutputStream out = new MultiBufferOutputStream();
                                OutputStreamWriter streamWriter = new OutputStreamWriter(out);
                                GSonUtils.encode(streamWriter, httpOutputData.getGlob(), false);
                                try {
                                    streamWriter.close();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                List<ByteBuffer> data = out.data();
                                stream = () -> data.isEmpty() ? null : data.remove(0);
                                responseSize = out.size();
                            } else {
                                HttpOutputData.SizedStream data = httpOutputData.getStream();
                                if (data == null || data.size() == 0L) {
                                    send204();
                                    return;
                                }
                                stream = new DataToSendProvider() {
                                    byte[] buffer = new byte[8192]; // can be reused

                                    public ByteBuffer nextBufferToSend() {
                                        int read = 0;
                                        try {
                                            read = data.stream().read(buffer);
                                        } catch (IOException e) {
                                            return null;
                                        }
                                        if (read < 0) {
                                            return null;
                                        }
                                        return ByteBuffer.wrap(buffer, 0, read);
                                    }
                                };
                                responseSize = data.size();
                            }
                            sendHttpResponse(new BasicHttpResponse(200), new BasicEntityDetails(responseSize,
                                    ContentType.APPLICATION_JSON));
                        } else {
                            send204();
                        }
                    });
        } catch (Exception ex) {
            manageException(ex);
        }
    }

    private void manageException(Throwable throwable) {
        if (throwable instanceof HttpExceptionWithContent) {
            sendStatus(((HttpExceptionWithContent) throwable).getCode(),
                    GSonUtils.encodeWithoutKind(((HttpExceptionWithContent) throwable).getContent()),
                    ContentType.APPLICATION_JSON
            );
        } else if (throwable instanceof org.globsframework.http.HttpException) {
            sendStatusWithReason(((org.globsframework.http.HttpException) throwable).getCode(),
                    ((org.globsframework.http.HttpException) throwable).getOriginalMessage());
        } else {
            send500(throwable);
        }
    }

    private void send500(Throwable ex) {
        LOGGER.error("Fail to handle request", ex);
        sendHttpResponse(new BasicHttpResponse(500), null);
    }

    private void sendStatusWithReason(int statusCode, String reason) {
        LOGGER.info("Response code " + statusCode + " : " + reason);
        sendHttpResponse(new BasicHttpResponse(statusCode, reason), null);
    }

    private void sendStatus(int statusCode, String message, ContentType contentType) {
        LOGGER.info("Response code " + statusCode + " : " + message);
        byte[] data = message.getBytes(UTF_8);
        AtomicReference<ByteBuffer> wrap = new AtomicReference<>(ByteBuffer.wrap(data));
        stream = () -> {
            try {
                return wrap.get();
            } finally {
                wrap.set(null);
            }
        };
        sendHttpResponse(new BasicHttpResponse(statusCode), new BasicEntityDetails(data.length, contentType));
    }

    private void sendHttpResponse(BasicHttpResponse statusCode,
                                  EntityDetails responseEntityDetails) {
        try {
            responseChannel.sendResponse(statusCode, responseEntityDetails, context);
        } catch (HttpException e) {
            LOGGER.error("Fail to send response (http error)", e);
        } catch (IOException e) {
            LOGGER.error("Fail to send response (io error)", e);
        }
    }

    private void responseFromHttpContent(Glob glob) {
            var ref = new Object() {
                byte[] bytes = glob.get(GlobHttpContent.content, EMPTY_BYTE_ARRAY);
            };
            stream = ref.bytes.length > 0 ? () -> {
                try {
                    return ref.bytes != null ? ByteBuffer.wrap(ref.bytes) : null;
                } finally {
                    ref.bytes = null;
                }
            } : null;
        sendHttpResponse(new BasicHttpResponse(glob.get(GlobHttpContent.statusCode, ref.bytes.length == 0 ? 204 : 200)),
                ref.bytes.length == 0 ? null : new BasicEntityDetails(ref.bytes.length,
                        ContentType.create(
                                glob.get(GlobHttpContent.mimeType, "application/octet-stream"),
                                glob.get(GlobHttpContent.charset))));
    }

    private void responseCustomHttpContent(Glob glob) {
        try {
            GlobType globType = glob.getType();
            Field fieldWithStatusCode = globType.findFieldWithAnnotation(StatusCode.UNIQUE_KEY);
            Field fieldWithData = globType.findFieldWithAnnotation(HttpBodyData.UNIQUE_KEY);

            int statusCode;
            String strData;
            if (fieldWithStatusCode instanceof IntegerField statusField
                    && (fieldWithData instanceof GlobField || fieldWithData instanceof GlobArrayField)) {

                if (fieldWithData instanceof GlobField globDataField) {
                    Glob data = glob.get(globDataField);
                    strData = data != null ? GSonUtils.encode(data, false) : null;
                } else {
                    Glob[] data = glob.get((GlobArrayField) fieldWithData);
                    strData = data != null ? GSonUtils.encode(data, false) : null;
                }

                statusCode = glob.get(statusField, strData == null ? 204 : 200);

                var ref = new Object() {
                    byte[] bytes = strData != null ? strData.getBytes(UTF_8) : null;
                };
                stream = ref.bytes != null ? () -> {
                    try {
                        return ref.bytes != null ? ByteBuffer.wrap(ref.bytes) : null;
                    } finally {
                        ref.bytes = null;
                    }
                } : null;
                responseChannel.sendResponse(
                        new BasicHttpResponse(statusCode),
                        ref.bytes == null ? null :
                                new BasicEntityDetails(ref.bytes.length,
                                        ContentType.APPLICATION_JSON), context);
            }
        } catch (HttpException e) {
            LOGGER.error("Fail to send response (http error)", e);
        } catch (IOException e) {
            LOGGER.error("Fail to send response (io error)", e);
        }
    }

    private void send204() {
        sendHttpResponse(new BasicHttpResponse(204), null);
    }

    interface DataToSendProvider {
        ByteBuffer nextBufferToSend();
    }

    // synchronized because call at sendResponse but can also be called but listen port on io write allowed.
    public synchronized void produceResponse(DataStreamChannel channel) throws IOException {
        if (currentResponseBuffer != null && currentResponseBuffer.hasRemaining()) {
            channel.write(currentResponseBuffer);
        } else {
            if (stream != null) {
                currentResponseBuffer = stream.nextBufferToSend();
                if (currentResponseBuffer != null) {
                    channel.write(currentResponseBuffer);
                }
            }
        }
        if ((currentResponseBuffer == null || !currentResponseBuffer.hasRemaining()) && stream != null) {
            currentResponseBuffer = stream.nextBufferToSend();
            if (currentResponseBuffer == null || !currentResponseBuffer.hasRemaining()) {
                channel.endStream(List.of());
            }
        }
    }

    @Override
    public int availableInResponse() {
        if (currentResponseBuffer != null && currentResponseBuffer.hasRemaining()) {
            return currentResponseBuffer.remaining();
        }
        if (stream != null) {
            currentResponseBuffer = stream.nextBufferToSend();
            if (currentResponseBuffer != null) {
                return currentResponseBuffer.remaining();
            }
        }
        return 0;
    }

    @Override
    public void releaseResources() {
    }

    @Override
    public void updateCapacityToReceiveData(CapacityChannel capacityChannel) {

    }

    @Override
    public void failed(Exception cause) {
        LOGGER.error("Fail to handle request", cause);
    }

//        private static byte[] getBoundaryIfMultipart(Header[] headers) {
//        byte[] boundary = null;
//        if (headers == null || headers.length == 0) {
//            return null;
//        }
//        for (Header element : headers) {
//            if (element.getName().equals(ContentType.MULTIPART_FORM_DATA.getMimeType())) {
//                for (NameValuePair parameter : element.getParameters()) {
//                    if (parameter.getName().equals("boundary")) {
//                        boundary = parameter.getValue().getBytes(Consts.ISO_8859_1);
//                    }
//                }
//            }
//        }
//        return boundary;
//    }

    // TODO =>feed a glob
    public static class MultipartInputStream extends InputStream {
        private final MultipartStream multipartStream;
        final ReusableByteArrayOutputStream output = new ReusableByteArrayOutputStream();
        private byte[] content;
        private boolean hasMore;
        int currentPos;
        private int size;

        public MultipartInputStream(MultipartStream multipartStream) throws IOException {
            this.multipartStream = multipartStream;
            hasMore = multipartStream.skipPreamble();
            if (hasMore) {
                readNext();
            }
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (currentPos < size) {
                int realLen = Math.min(size - currentPos, len);
                System.arraycopy(content, currentPos, b, off, realLen);
                currentPos += realLen;
                return realLen;
            } else {
                if (hasMore) {
                    readNext();
                    return read(b, off, len);
                } else {
                    return -1;
                }
            }
        }

        public int read() throws IOException {
            if (currentPos < size) {
                return content[currentPos++] & 0xff;
            }
            if (hasMore) {
                readNext();
            } else {
                return -1;
            }
            return read();
        }

        private void readNext() throws IOException {
            LOGGER.debug("Read next part");
            multipartStream.readHeaders();
            output.reset();
            multipartStream.readBodyData(output);
            content = output.getBuffer();
            currentPos = 0;
            size = output.size();
            hasMore = multipartStream.readBoundary();
        }
    }

}
