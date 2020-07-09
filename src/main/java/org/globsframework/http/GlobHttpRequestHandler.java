package org.globsframework.http;

import org.apache.http.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HttpContext;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotationType;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.Files;
import org.globsframework.utils.StringConverter;
import org.globsframework.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class GlobHttpRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {
    private static Logger LOGGER = LoggerFactory.getLogger(GlobHttpRequestHandler.class);
    private final UrlMatcher urlMatcher;
    private HttpHandler onPost;
    private HttpHandler onPut;
    private HttpHandler onDelete;
    private HttpHandler onGet;
    private HttpReceiver httpReceiver;

    public GlobHttpRequestHandler(HttpReceiver httpReceiver) {
        this.httpReceiver = httpReceiver;
        if (httpReceiver.getUrlType() != null) {
            urlMatcher = new DefaultUrlMatcher(httpReceiver.getUrlType(), httpReceiver.getUrl());
        } else {
            urlMatcher = new UrlMatcher() {
                public Glob parse(String url) {
                    return null;
                }
            };
        }
        for (HttpOperation operation : httpReceiver.getOperations()) {
            switch (operation.verb()) {
                case post:
                    onPost = new HttpHandler(operation);
                    break;
                case put:
                    onPut = new HttpHandler(operation);
                    break;
                case delete:
                    onDelete = new HttpHandler(operation);
                    break;
                case get:
                    onGet = new HttpHandler(operation);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + operation.verb());
            }
        }
    }

    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
        return new BasicAsyncRequestConsumer();
    }

    public String createRegExp() {
        String[] split = httpReceiver.getUrl().split("/");
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : split) {
            if (!s.isEmpty()) {
                stringBuilder.append("/");
                if (s.startsWith("{") && s.endsWith("}")) {
                    stringBuilder.append("*");
                    return stringBuilder.toString(); // break at first {}
                } else {
                    stringBuilder.append(s);
                }
            }
        }
        return stringBuilder.toString();
    }

    public void handle(HttpRequest httpRequest, HttpAsyncExchange httpAsyncExchange, HttpContext httpContext) throws HttpException, IOException {
        RequestLine requestLine = httpRequest.getRequestLine();
        String method = requestLine.getMethod().toUpperCase(Locale.ROOT);
        LOGGER.info("Receive : " + method + " : " + requestLine.getUri());
        final HttpResponse response = httpAsyncExchange.getResponse();
        try {
            if (method.equals("DELETE")) {
                treatOp(requestLine, httpAsyncExchange, httpRequest, response, onDelete);
            } else if (method.equals("POST")) {
                treatOp(requestLine, httpAsyncExchange, httpRequest, response, onPost);
            } else if (method.equals("PUT")) {
                treatOp(requestLine, httpAsyncExchange, httpRequest, response, onPut);
            } else if (method.equals("GET")) {
                treatOp(requestLine, httpAsyncExchange, httpRequest, response, onGet);
            } else {
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        } catch (Exception e) {
            LOGGER.error("For " + requestLine.getUri(), e);
            response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
            httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
        LOGGER.info("done " + requestLine.getUri());
    }

    private void treatOp(RequestLine requestLine, HttpAsyncExchange httpAsyncExchange, HttpRequest httpRequest, HttpResponse response, HttpHandler httpHandler) throws IOException {
        try {
            if (httpHandler == null) {
                LOGGER.error("Receive unexpected request (" + requestLine.getMethod() + ")");
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
            } else if (httpRequest instanceof HttpEntityEnclosingRequest) {
                HttpOperation operation = httpHandler.operation;
                HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
                Glob data;
                Runnable deleteFile;
                if (operation.getBodyType() == GlobFile.TYPE) {
                    File tempFile = File.createTempFile("htpp", ".data");
                    FileOutputStream outputStream = new FileOutputStream(tempFile);
                    Files.copyStream(entity.getContent(), outputStream);
                    outputStream.close();
                    data = GlobFile.TYPE.instantiate().set(GlobFile.file, tempFile.getAbsolutePath());
                    deleteFile = () -> {
                        if (tempFile.exists() && !tempFile.delete()) {
                            LOGGER.error("Fail to delete" + tempFile.getAbsolutePath());
                        }
                    };
                } else {
                    String str = Files.read(entity.getContent(), StandardCharsets.UTF_8);
                    LOGGER.info("receive : " + str);
                    data = (Strings.isNullOrEmpty(str) || operation.getBodyType() == null) ? null : GSonUtils.decode(new StringReader(str), operation.getBodyType());
                    deleteFile = () -> {};
                }
                String uri = requestLine.getUri();
                int i = uri.indexOf("?");
                String urlStr = uri.substring(0, i == -1 ? uri.length() : i);
                String paramStr = i == -1 ? null : uri.substring(i + 1);
                Glob url = urlMatcher.parse(urlStr);
                Glob queryParam = httpHandler.teatParam(paramStr);
                CompletableFuture<Glob> future = operation.consume(data, url, queryParam);
                if (future != null) {
                    future.whenComplete((glob, throwable) -> {
                        if (glob != null) {
                            response.setEntity(new StringEntity(GSonUtils.encode(glob, false),
                                    ContentType.create("application/json", StandardCharsets.UTF_8)));
                            response.setStatusCode(HttpStatus.SC_OK);
                        } else {
                            if (throwable != null) {
                                LOGGER.error("Request fail", throwable);
                                if (throwable instanceof HttpException) {
                                    int statusCode = ((HttpException) throwable).code;
                                    LOGGER.error("return status: " + statusCode);
                                    response.setStatusCode(statusCode);
                                    response.setReasonPhrase(((HttpException) throwable).message);
                                } else if (throwable instanceof HttpExceptionWithContent) {
                                    response.setEntity(new StringEntity(GSonUtils.encode(((HttpExceptionWithContent) throwable).message, false),
                                            ContentType.create("application/json", StandardCharsets.UTF_8)));
                                    int statusCode = ((HttpExceptionWithContent) throwable).code;
                                    LOGGER.error("return status: " + statusCode);
                                    response.setStatusCode(statusCode);
                                } else {
                                    LOGGER.error("return status: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                    response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                }
                            } else {
                                response.setStatusCode(HttpStatus.SC_OK);
                            }
                        }
                        httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                        deleteFile.run();
                    });
                } else {
                    deleteFile.run();
                    response.setStatusCode(HttpStatus.SC_OK);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                }
            } else if (httpRequest instanceof BasicHttpRequest) { //GET
                String uri = requestLine.getUri();
                HttpOperation operation = httpHandler.operation;
                int i = uri.indexOf("?");
                String urlStr = uri.substring(0, i == -1 ? uri.length() : i);
                String paramStr = i == -1 ? null : uri.substring(i + 1);
                Glob url = urlMatcher.parse(urlStr);
                Glob queryParam = httpHandler.teatParam(paramStr);
                CompletableFuture<Glob> glob = operation.consume(null, url, queryParam);
                if (glob != null) {
                    glob.whenComplete((res, throwable) -> {
                        if (res != null) {
                            if (res.getType() == GlobFile.TYPE) {
                                NFileEntity entity;
                                final File file = new File(res.get(GlobFile.file));
                                if (res.get(GlobFile.removeWhenDelivered, !LOGGER.isTraceEnabled())) {
                                    entity = new NFileEntity(file,
                                            ContentType.create("application/json", StandardCharsets.UTF_8)) {
                                        public void close() throws IOException {
                                            super.close();
                                            if (!file.delete() && file.exists()) {
                                                LOGGER.error("Fail to delete " + file.getAbsolutePath());
                                            }
                                        }
                                    };
                                } else {
                                    entity = new NFileEntity(file,
                                            ContentType.create("application/json", StandardCharsets.UTF_8));
                                }
                                response.setEntity(entity);
                            } else {
                                String resp = GSonUtils.encode(res, false);
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("response {}", resp);
                                }
                                response.setEntity(new StringEntity(resp,
                                        ContentType.create("application/json", StandardCharsets.UTF_8)));
                            }
                            response.setStatusCode(HttpStatus.SC_OK);
                        } else {
                            if (throwable != null) {
                                LOGGER.error("Request fail", throwable);
                                LOGGER.error("return status: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                            } else {
                                response.setStatusCode(HttpStatus.SC_OK);
                            }
                        }
                        httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                    });
                } else {
                    response.setStatusCode(HttpStatus.SC_OK);
                    httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
                }
            } else {
                LOGGER.error("Unexpected type " + httpRequest.getClass().getName());
                LOGGER.error("return status: " + HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                httpAsyncExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        } catch (Exception e) {
            LOGGER.error("request error", e);
            LOGGER.error("return status: " + HttpStatus.SC_FORBIDDEN);
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

    public interface ParamProcessor {
        Glob treat(String queryParams);
    }

    public static class HttpHandler {
        private final HttpOperation operation;
        private final ParamProcessor paramProcessor;

        public HttpHandler(HttpOperation operation) {
            this.operation = operation;
            paramProcessor = operation.getQueryParamType() == null ? allHeaders -> null : new DefaultParamProcessor(operation.getQueryParamType());
        }

        public Glob teatParam(String queryParam) {
            return paramProcessor.treat(queryParam);
        }
    }

    public static class DefaultParamProcessor implements ParamProcessor {
        GlobType paramType;
        Map<String, StringConverter.FromStringConverter> converterMap = new HashMap<>();

        public DefaultParamProcessor(GlobType paramType) {
            this.paramType = paramType;
            for (Field field : paramType.getFields()) {
                converterMap.put(FieldNameAnnotationType.getName(field), StringConverter.createConverter(field, ","));
            }
        }

        public Glob treat(String queryParams) {
            if (Strings.isNotEmpty(queryParams)) {
                MutableGlob instantiate = paramType.instantiate();
                List<NameValuePair> parse = URLEncodedUtils.parse(queryParams, StandardCharsets.UTF_8);
                for (NameValuePair nameValuePair : parse) {
                    StringConverter.FromStringConverter fromStringConverter = converterMap.get(nameValuePair.getName());
                    if (fromStringConverter != null) {
                        fromStringConverter.convert(instantiate, nameValuePair.getValue());
                    } else {
                        LOGGER.error("unexpected param " + nameValuePair.getName());
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

}
