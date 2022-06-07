package org.globsframework.http;

import com.google.gson.Gson;
import org.apache.http.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.globsframework.http.model.DataAnnotationType;
import org.globsframework.http.model.StatusCode;
import org.globsframework.http.model.StatusCodeAnnotationType;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotationType;
import org.globsframework.metamodel.annotations.RequiredAnnotationType;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.Files;
import org.globsframework.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * HttpAsyncRequestHandler for globs framework.
 * */
public class GlobHttpRequestHandler  {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobHttpRequestHandler.class);

    private final UrlMatcher urlMatcher;
    private final boolean gzipCompress;
    private String serverInfo;
    private final HttpReceiver httpReceiver;
    private HttpHandler onPost;
    private HttpHandler onPut;
    private HttpHandler onPatch;
    private HttpHandler onDelete;
    private HttpHandler onGet;
    private HttpHandler onOption;

    public GlobHttpRequestHandler(String serverInfo, HttpReceiver httpReceiver, boolean gzipCompress) {
        this.serverInfo = serverInfo;
        this.httpReceiver = httpReceiver;
        this.gzipCompress = gzipCompress;
        this.urlMatcher = DefaultUrlMatcher.create(httpReceiver.getUrlType(), httpReceiver.getUrl());
        for (HttpOperation operation : httpReceiver.getOperations()) {
            switch (operation.verb()) {
                case post:
                    onPost = new HttpHandler(serverInfo, operation);
                    break;
                case put:
                    onPut = new HttpHandler(serverInfo, operation);
                    break;
                case patch:
                    onPatch = new HttpHandler(serverInfo, operation);
                    break;
                case delete:
                    onDelete = new HttpHandler(serverInfo, operation);
                    break;
                case get:
                    onGet = new HttpHandler(serverInfo, operation);
                    break;
                case option:
                    onOption = new HttpHandler(serverInfo, operation);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + operation.verb());
            }
        }
    }

    /**
     * Creates a regular expression based on the url pattern.
     * For example /foo/{A}/bar becomes /foo/\*\/bar
     * @return a regexp of the url pattern
     */


    public Collection<String> createRegExp() {
        String[] split = httpReceiver.getUrl().split("/");
        List<String> matcher = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : split) {
            if (!s.isEmpty()) {
                if (s.startsWith("{") && s.endsWith("}")) {
                    matcher.add(null);
                } else {
                    matcher.add(s);
                    stringBuilder.append(s);
                }
            }
        }
        LOGGER.debug(serverInfo + " regex: {}", stringBuilder);
        return matcher;
    }

    public void handle(String[] path, String paramStr, HttpRequest httpRequest, HttpAsyncExchange httpAsyncExchange, HttpContext httpContext) throws HttpException, IOException {
        RequestLine requestLine = httpRequest.getRequestLine();
        String method = requestLine.getMethod().toUpperCase(Locale.ROOT);
        LOGGER.info("{} Receive : {} : {}", serverInfo, method, requestLine.getUri());
        final HttpResponse response = httpAsyncExchange.getResponse();
        try {
            switch (method) {
                case "DELETE":
                    treatOp(path, paramStr, requestLine, httpAsyncExchange, httpRequest, response, onDelete);
                    break;
                case "POST":
                    treatOp(path, paramStr, requestLine, httpAsyncExchange, httpRequest, response, onPost);
                    break;
                case "PUT":
                    treatOp(path, paramStr, requestLine, httpAsyncExchange, httpRequest, response, onPut);
                    break;
                case "PATCH":
                    treatOp(path, paramStr, requestLine, httpAsyncExchange, httpRequest, response, onPatch);
                    break;
                case "GET":
                    treatOp(path, paramStr, requestLine, httpAsyncExchange, httpRequest, response, onGet);
                    break;
                case "OPTIONS":
                    response.setStatusCode(HttpStatus.SC_OK);
                    this.httpReceiver.headers(response::addHeader);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                    break;
                default:
                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                    break;
            }
        } catch (Exception e) {
            LOGGER.error(serverInfo + " For " + requestLine.getUri(), e);
            response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
            httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
        LOGGER.info("{} done {}", serverInfo, requestLine.getUri());
    }

    private void treatOp(String[] path, String paramStr, RequestLine requestLine, HttpAsyncExchange httpAsyncExchange, HttpRequest httpRequest,
                         HttpResponse response, HttpHandler httpHandler) throws IOException {
        try {
            if (httpHandler == null) {
                LOGGER.error("{} : Receive unexpected request ({})", serverInfo, requestLine.getMethod());
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
            } else if (httpRequest instanceof HttpEntityEnclosingRequest) {
                HttpOperation operation = httpHandler.operation;
                HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
                Glob data;
                Runnable deleteFile;
                if (operation.getBodyType() == GlobHttpContent.TYPE) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    Files.copyStream(entity.getContent(), outputStream);
                    data = GlobHttpContent.TYPE.instantiate()
                            .set(GlobHttpContent.content, outputStream.toByteArray());
                    deleteFile = () -> {
                    };
                } else if (operation.getBodyType() == GlobFile.TYPE) {
                    File tempFile = File.createTempFile("http", ".data");
                    try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                        Files.copyStream(entity.getContent(), outputStream);
                    }

                    data = GlobFile.TYPE.instantiate().set(GlobFile.file, tempFile.getAbsolutePath());
                    deleteFile = () -> {
                        if (tempFile.exists() && !tempFile.delete()) {
                            LOGGER.error("{} : Fail to delete {}", serverInfo, tempFile.getAbsolutePath());
                        }
                    };
                } else {
                    //find mimetype (if xml => produce xml)
//                    Arrays.stream(entity.getContentType().getElements())
//                            .filter(headerElement -> headerElement.getName().equals())
                    boolean hid = operation.hasSensitiveData();
                    String str = Files.read(entity.getContent(), StandardCharsets.UTF_8);
                    if (!hid) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("{} : receive : {}", serverInfo, str);
                        } else {
                            LOGGER.info("{} : receive : {}", serverInfo, str.substring(0, Math.min(10000, str.length())));
                        }
                    }
                    data = (Strings.isNullOrEmpty(str) || operation.getBodyType() == null) ? null : GSonUtils.decode(str, operation.getBodyType());
                    if (hid) {
                        LOGGER.info("{} : receive : {}", serverInfo, GSonUtils.encodeHidSensitiveData(data));
                    }
                    deleteFile = () -> {
                    };
                }
                Glob url = urlMatcher.parse(path);
                Glob queryParam = httpHandler.teatParam(paramStr);
                CompletableFuture<Glob> future = operation.consume(data, url, queryParam);
                if (future != null) {
                    future.whenComplete((glob, throwable) -> {
                        if (glob != null) {
                            GlobType globType = glob.getType();

                            if (globType == GlobHttpContent.TYPE) {
                                response.setEntity(new ByteArrayEntity(glob.get(GlobHttpContent.content),
                                        ContentType.create(glob.get(GlobHttpContent.mimeType, "application/octet-stream"),
                                                glob.get(GlobHttpContent.charset) != null ? Charset.forName(glob.get(GlobHttpContent.charset)) : null)));
                            } else if (globType == GlobFile.TYPE) {
                                NFileEntity returnEntity;
                                final File file = new File(glob.get(GlobFile.file));
                                if (glob.get(GlobFile.removeWhenDelivered, !LOGGER.isTraceEnabled())) {
                                    returnEntity = new NFileEntity(file,
                                            ContentType.create(glob.get(GlobFile.mimeType, "application/json"), StandardCharsets.UTF_8)) {
                                        @Override
                                        public void close() throws IOException {
                                            super.close();
                                            if (!file.delete() && file.exists()) {
                                                LOGGER.error("{} : Fail to delete {}", serverInfo, file.getAbsolutePath());
                                            }
                                        }
                                    };
                                } else {
                                    returnEntity = new NFileEntity(file,
                                            ContentType.create(glob.get(GlobFile.mimeType, "application/json"), StandardCharsets.UTF_8));
                                }
                                response.setEntity(returnEntity);
                            } else {
                                if (gzipCompress) {
                                    try {
                                        ArrayOutputInputStream out = new ArrayOutputInputStream();
                                        OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8);
                                        GSonUtils.encode(writer, glob, false);
                                        writer.close();
                                        response.setHeader(new BasicHeader(HTTP.CONTENT_ENCODING, "gzip"));
                                        response.setEntity(new InputStreamEntity(out.getInputStream()));
                                        response.setStatusCode(HttpStatus.SC_OK);
                                    } catch (IOException e) {
                                        LOGGER.error(serverInfo + " : Bug io error", e);
                                        response.setStatusCode(HttpStatus.SC_METHOD_FAILURE);
                                    }
                                } else {
                                    Field fieldWithStatusCode = globType.findFieldWithAnnotation(StatusCodeAnnotationType.UNIQUE_KEY);
                                    Field fieldWithData = globType.findFieldWithAnnotation(DataAnnotationType.UNIQUE_KEY);

                                    Integer statusCode;
                                    String strData;
                                    if (fieldWithStatusCode instanceof IntegerField
                                            && (fieldWithData instanceof GlobField || fieldWithData instanceof GlobArrayField)) {
                                        statusCode = glob.get((IntegerField) fieldWithStatusCode);

                                        if (fieldWithData instanceof GlobField) {
                                            strData = GSonUtils.encode(glob.get((GlobField) fieldWithData), false);
                                        } else {
                                            strData = GSonUtils.encode(glob.get((GlobArrayField) fieldWithData), false);
                                        }
                                    } else {
                                        statusCode = HttpStatus.SC_OK;
                                        strData = GSonUtils.encode(glob, false);
                                    }

                                    response.setEntity(new StringEntity(strData,
                                            ContentType.create("application/json", StandardCharsets.UTF_8)));
                                    response.setStatusCode(statusCode);
                                }
                            }
                        } else {
                            if (throwable != null) {
                                LOGGER.error(serverInfo + " : Request fail", throwable);
                                if (throwable instanceof HttpException) {
                                    int statusCode = ((HttpException) throwable).code;
                                    LOGGER.error("{} : return status: {}", serverInfo, statusCode);
                                    response.setStatusCode(statusCode);
                                    response.setReasonPhrase(((HttpException) throwable).message);
                                } else if (throwable instanceof HttpExceptionWithContent) {
                                    response.setEntity(new StringEntity(GSonUtils.encode(((HttpExceptionWithContent) throwable).message, false),
                                            ContentType.create("application/json", StandardCharsets.UTF_8)));
                                    int statusCode = ((HttpExceptionWithContent) throwable).code;
                                    LOGGER.error("{} : return status: {}", serverInfo, statusCode);
                                    response.setStatusCode(statusCode);
                                } else {
                                    LOGGER.error(serverInfo +" : return status: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                    response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                }
                            } else {
                                response.setStatusCode(HttpStatus.SC_OK);
                            }
                        }
                        operation.headers(response::addHeader);
                        httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                        deleteFile.run();
                    });
                } else {
                    deleteFile.run();
                    response.setStatusCode(HttpStatus.SC_OK);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                }
            } else if (httpRequest instanceof BasicHttpRequest) { //GET
                HttpOperation operation = httpHandler.operation;
                Glob url = urlMatcher.parse(path);
                Glob queryParam = httpHandler.teatParam(paramStr);
                CompletableFuture<Glob> glob = operation.consume(null, url, queryParam);
                if (glob != null) {
                    glob.whenComplete((res, throwable) -> {
                        if (res != null) {
                            if (res.getType() == GlobHttpContent.TYPE) {
                                response.setEntity(new ByteArrayEntity(res.get(GlobHttpContent.content),
                                        ContentType.create(res.get(GlobHttpContent.mimeType, "application/octet-stream"),
                                                res.get(GlobHttpContent.charset) != null ? Charset.forName(res.get(GlobHttpContent.charset)) : null)));
                            } else if (res.getType() == GlobFile.TYPE) {
                                NFileEntity entity;
                                final File file = new File(res.get(GlobFile.file));
                                if (res.get(GlobFile.removeWhenDelivered, !LOGGER.isTraceEnabled())) {
                                    entity = new NFileEntity(file,
                                            ContentType.create(res.get(GlobFile.mimeType, "application/json"), StandardCharsets.UTF_8)) {
                                        @Override
                                        public void close() throws IOException {
                                            super.close();
                                            if (!file.delete() && file.exists()) {
                                                LOGGER.error("{} : Fail to delete {}", serverInfo, file.getAbsolutePath());
                                            }
                                        }
                                    };
                                } else {
                                    entity = new NFileEntity(file,
                                            ContentType.create(res.get(GlobFile.mimeType, "application/json"), StandardCharsets.UTF_8));
                                }
                                response.setEntity(entity);
                            } else {
                                if (gzipCompress) {
                                    ArrayOutputInputStream out = new ArrayOutputInputStream();
                                    GSonUtils.encode(new OutputStreamWriter(out, StandardCharsets.UTF_8), res, false);
                                    response.setHeader(new BasicHeader(HTTP.CONTENT_ENCODING, "gzip"));
                                    try {
                                        response.setEntity(new InputStreamEntity(new GZIPInputStream(out.getInputStream())));
                                        response.setStatusCode(HttpStatus.SC_OK);
                                    } catch (IOException e) {
                                        LOGGER.error("Bug io error on GZIP", e);
                                        response.setStatusCode(HttpStatus.SC_METHOD_FAILURE);
                                    }
                                } else {
                                    response.setEntity(new StringEntity(GSonUtils.encode(res, false),
                                            ContentType.create("application/json", StandardCharsets.UTF_8)));
                                    response.setStatusCode(HttpStatus.SC_OK);
                                }
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("{} : response {}", serverInfo, GSonUtils.encode(res, false));
                                }
                            }
                            response.setStatusCode(HttpStatus.SC_OK);
                        } else {
                            if (throwable != null) {
                                LOGGER.error(serverInfo + " : Request fail", throwable);
                                LOGGER.error(serverInfo + " : return status: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                            } else {
                                response.setStatusCode(HttpStatus.SC_OK);
                            }
                        }
                        operation.headers(response::addHeader);
                        httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                    });
                } else {
                    response.setStatusCode(HttpStatus.SC_OK);
                    operation.headers(response::addHeader);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                }
            } else {
                LOGGER.error("{} : Unexpected type {}", serverInfo, httpRequest.getClass().getName());
                LOGGER.error("{} : return status: {}", serverInfo, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        } catch (Exception e) {
            LOGGER.error(serverInfo + " : request error", e);
            LOGGER.error("{} return status: {}", serverInfo, HttpStatus.SC_FORBIDDEN);
            response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
    }

    public static DefaultHttpOperation post(HttpTreatment function, GlobType bodyType) {
        return post(function, bodyType, null);
    }

    public static DefaultHttpOperation post(HttpTreatment function, GlobType bodyType, GlobType queryType) {
        return new DefaultHttpOperation(HttpOp.post, bodyType, queryType, function);
    }

    public static DefaultHttpOperation get(HttpTreatment function) {
        return get(function, null);
    }

    public static DefaultHttpOperation get(HttpTreatment function, GlobType queryType) {
        return new DefaultHttpOperation(HttpOp.get, null, queryType, function);
    }

    public static DefaultHttpOperation delete(HttpTreatment function) {
        return delete(function, null);
    }

    public static DefaultHttpOperation delete(HttpTreatment function, GlobType queryType) {
        return new DefaultHttpOperation(HttpOp.delete, null, queryType, function);
    }

    public boolean hasWildcardAtEnd() {
        return urlMatcher.withWildCard();
    }

    public interface ParamProcessor {
        Glob treat(String queryParams);
    }

    public static class HttpHandler {
        private final String serverInfo;
        private final HttpOperation operation;
        private final ParamProcessor paramProcessor;

        public HttpHandler(String serverInfo, HttpOperation operation) {
            this.serverInfo = serverInfo;
            this.operation = operation;
            paramProcessor = operation.getQueryParamType() == null ? allHeaders -> null : new DefaultParamProcessor(this.serverInfo, operation.getQueryParamType());
        }

        public Glob teatParam(String queryParam) {
            return paramProcessor.treat(queryParam);
        }
    }

    public static class DefaultParamProcessor implements ParamProcessor {
        private final String serverInfo;
        GlobType paramType;
        Map<String, GlobHttpUtils.FromStringConverter> converterMap = new HashMap<>();

        public DefaultParamProcessor(String serverInfo, GlobType paramType) {
            this.serverInfo = serverInfo;
            this.paramType = paramType;
            for (Field field : paramType.getFields()) {
                converterMap.put(FieldNameAnnotationType.getName(field), GlobHttpUtils.createConverter(field, ","));
            }
        }

        public Glob treat(String queryParams) {
            if (Strings.isNotEmpty(queryParams)) {
                MutableGlob instantiate = paramType.instantiate();
                List<NameValuePair> parse = URLEncodedUtils.parse(queryParams, StandardCharsets.UTF_8);
                for (NameValuePair nameValuePair : parse) {
                    GlobHttpUtils.FromStringConverter fromStringConverter = converterMap.get(nameValuePair.getName());
                    if (fromStringConverter != null) {
                        fromStringConverter.convert(instantiate, nameValuePair.getValue());
                    } else {
                        LOGGER.error("{} : unexpected param {}", serverInfo, nameValuePair.getName());
                    }
                }
                return instantiate;
            }
            return null;
        }
    }

    public static class EmptyType {
        public static GlobType TYPE;

        static {
            GlobTypeLoaderFactory.create(EmptyType.class).load();
        }
    }

    private static class ArrayOutputInputStream extends ByteArrayOutputStream {
        int at = 0;

        InputStream getInputStream() {
            return new InputStream() {
                public int read() throws IOException {
                    return at < count ? buf[at++] : -1;
                }
            };
        }
    }
}
