package org.globsframework.http;

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
import org.globsframework.http.model.HttpBodyData;
import org.globsframework.http.model.HttpGlobResponse;
import org.globsframework.http.model.StatusCode;
import org.globsframework.json.GSonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

class DefaultGlobHttpRequestHandler implements GlobHttpRequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.globsframework.http.DefaultGlobHttpRequestHandler");
    private final HttpOperation operation;
    private final Glob urlGlob;
    private final Glob paramType;
    private final HttpRequest request;
    private final EntityDetails entityDetails;
    private final ResponseChannel responseChannel;
    private final HttpContext context;
    private final Glob header;
    private DataToSendProvider stream;
    private MultiByteArrayInputStream multiByteArrayInputStream;
    private long responseSize;
    private ByteBuffer currentResponseBuffer;

    public DefaultGlobHttpRequestHandler(HttpOperation operation, Glob urlGlob, Glob paramType, HttpRequest request,
                                         EntityDetails entityDetails, ResponseChannel responseChannel, HttpContext context) {
        this.operation = operation;
        this.urlGlob = urlGlob;
        this.paramType = paramType;
        this.request = request;
        this.entityDetails = entityDetails;
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
                    callHandler(HttpInputData.fromStream(multiByteArrayInputStream, entityDetails.getContentLength()));
                }
            });
        } else {
            // already called in consumeRequest
        }
    }

    public void consumeRequest(ByteBuffer src) {
        if (multiByteArrayInputStream != null) {
            multiByteArrayInputStream.addBuffer(src);
        } else if (src.limit() - src.position() == entityDetails.getContentLength()) {
            HttpInputData inputData;
            if (operation.getBodyType() != null) {
                CharBuffer decode = UTF_8.decode(src);
                Glob glob = GSonUtils.decode(new Reader() {
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
                        if (httpOutputData != null) {
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
                            try {
                                responseChannel.sendResponse(new BasicHttpResponse(200),
                                        new BasicEntityDetails(responseSize,
                                                ContentType.APPLICATION_JSON), context);
                            } catch (org.apache.hc.core5.http.HttpException e) {
                                LOGGER.error("Fail to send response (http error)", e);
                            } catch (IOException e) {
                                LOGGER.error("Fail to send response (io error)", e);
                            }
                        } else {
                            send204();
                        }
                    });
        } catch (Exception ex) {
            try {
                LOGGER.error("Fail to handle request", ex);
                responseChannel.sendResponse(new BasicHttpResponse(500), null, context);
            } catch (org.apache.hc.core5.http.HttpException e) {
                LOGGER.error("Fail to send response (http error)", e);
            } catch (IOException e) {
                LOGGER.error("Fail to send response (io error)", e);
            }
        }
    }

    private void responseFromHttpContent(Glob glob) {
        try {
            var ref = new Object() {
                byte[] bytes = glob.get(GlobHttpContent.content);
            };
            stream = ref.bytes != null ? () -> {
                try {
                    return ref.bytes != null ? ByteBuffer.wrap(ref.bytes) : null;
                } finally {
                    ref.bytes = null;
                }
            } : null;
            responseChannel.sendResponse(
                    new BasicHttpResponse(glob.get(GlobHttpContent.statusCode, 200)),
                    new BasicEntityDetails(ref.bytes == null ? 0 : ref.bytes.length,
                            ContentType.create(
                                    glob.get(GlobHttpContent.mimeType),
                                    glob.get(GlobHttpContent.charset))), context);
        } catch (HttpException e) {
            LOGGER.error("Fail to send response (http error)", e);
        } catch (IOException e) {
            LOGGER.error("Fail to send response (io error)", e);
        }
    }

    private void responseCustomHttpContent(Glob glob) {
        try {
            GlobType globType = glob.getType();
            Field fieldWithStatusCode = globType.findFieldWithAnnotation(StatusCode.UNIQUE_KEY);
            Field fieldWithData = globType.findFieldWithAnnotation(HttpBodyData.UNIQUE_KEY);

            Integer statusCode;
            String strData;
            if (fieldWithStatusCode instanceof IntegerField statusField
                    && (fieldWithData instanceof GlobField || fieldWithData instanceof GlobArrayField)) {
                statusCode = glob.get(statusField);

                if (fieldWithData instanceof GlobField globDataField) {
                    Glob data = glob.get(globDataField);
                    strData = data != null ? GSonUtils.encode(data, false) : null;
                } else {
                    Glob[] data = glob.get((GlobArrayField) fieldWithData);
                    strData = data != null ? GSonUtils.encode(data, false) : null;
                }

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
                        new BasicEntityDetails(ref.bytes == null ? 0 : ref.bytes.length,
                                ContentType.APPLICATION_JSON), context);
            }
        } catch (HttpException e) {
            LOGGER.error("Fail to send response (http error)", e);
        } catch (IOException e) {
            LOGGER.error("Fail to send response (io error)", e);
        }
    }

    private void send204() {
        try {
            responseChannel.sendResponse(new BasicHttpResponse(204), null, context);
        } catch (HttpException e) {
            LOGGER.error("Fail to send response (http error)", e);
        } catch (IOException e) {
            LOGGER.error("Fail to send response (io error)", e);
        }
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
