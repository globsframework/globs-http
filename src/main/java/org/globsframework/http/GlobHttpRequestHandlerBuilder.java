package org.globsframework.http;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.FieldName;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GlobHttpRequestHandlerBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobHttpRequestHandlerBuilder.class);
    private final String serverInfo;
    private final HttpReceiver httpReceiver;
    private final UrlMatcher urlMatcher;
    private HttpHandler onPost;
    private HttpHandler onPut;
    private HttpHandler onPatch;
    private HttpHandler onDelete;
    private HttpHandler onGet;
    private HttpHandler onOption;

    public boolean hasWildcardAtEnd() {
        return urlMatcher.withWildCard();
    }


    public interface ParamProcessor {
        Glob treat(String queryParams);
    }

    public static class DefaultParamProcessor implements ParamProcessor {
        private final String serverInfo;
        GlobType paramType;
        Map<String, GlobHttpUtils.FromStringConverter> converterMap = new HashMap<>();

        public DefaultParamProcessor(String serverInfo, GlobType paramType) {
            this.serverInfo = serverInfo;
            this.paramType = paramType;
            for (Field field : paramType.getFields()) {
                converterMap.put(FieldName.getName(field), GlobHttpUtils.createConverter(field, ","));
            }
        }

        public Glob treat(String queryParams) {
            if (Strings.isNotEmpty(queryParams)) {
                MutableGlob instantiate = paramType.instantiateWithDefaults();
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


        public static class HttpHandler {
        private final String serverInfo;
        public final HttpOperation operation;
        public final ParamProcessor paramProcessor;

        public HttpHandler(String serverInfo, HttpOperation operation) {
            this.serverInfo = serverInfo;
            this.operation = operation;
            paramProcessor = operation.getQueryParamType() == null ? allHeaders -> null : new DefaultParamProcessor(this.serverInfo, operation.getQueryParamType());
        }

        public Glob teatParam(String queryParam) {
            return paramProcessor.treat(queryParam);
        }
    }

    public GlobHttpRequestHandlerBuilder(String serverInfo, HttpReceiver httpReceiver) {
        this.serverInfo = serverInfo;
        this.httpReceiver = httpReceiver;
        this.urlMatcher = DefaultUrlMatcher.create(httpReceiver.getUrlType(), httpReceiver.getUrl());
        for (HttpOperation operation : httpReceiver.getOperations()) {
            switch (operation.verb()) {
                case post -> onPost = new HttpHandler(serverInfo, operation);
                case put -> onPut = new HttpHandler(serverInfo, operation);
                case patch -> onPatch = new HttpHandler(serverInfo, operation);
                case delete -> onDelete = new HttpHandler(serverInfo, operation);
                case get -> onGet = new HttpHandler(serverInfo, operation);
                case option -> onOption = new HttpHandler(serverInfo, operation);
                default -> throw new IllegalStateException("Unexpected value: " + operation.verb());
            }
        }
    }


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
        LOGGER.debug("{} regex: {}", serverInfo, stringBuilder);
        return matcher;
    }

    public GlobHttpRequestHandlerFactory create(String[] path, String method, String paramStr, boolean hasBody) {
        if (method.equals(HttpHead.METHOD_NAME)) {
            return (request, entityDetails, responseChannel, context) ->
                        new ResponseGlobHttpRequestHandler(responseChannel, context, 403);
        }
        Glob urlGlob = urlMatcher.parse(path);
        HttpHandler httpHandler = switch (method) {
            case HttpGet.METHOD_NAME -> onGet;
            case HttpPost.METHOD_NAME -> onPost;
            case HttpPut.METHOD_NAME -> onPut;
            case HttpPatch.METHOD_NAME -> onPatch;
            case HttpDelete.METHOD_NAME -> onDelete;
            case HttpOptions.METHOD_NAME -> onOption;
            default -> throw new IllegalStateException("Unexpected value: " + method);
        };
        if (httpHandler == null) {
            if (method.equals(HttpOptions.METHOD_NAME)) {
                return (request, entityDetails, responseChannel, context) ->
                        new ResponseGlobHttpRequestHandler(responseChannel, context, 200);
            }
            throw new IllegalStateException("No route for " + String.join("/", path));
        }
        Glob paramType = httpHandler.teatParam(paramStr);
        return (request, entityDetails, responseChannel, context) ->
                new DefaultGlobHttpRequestHandler(httpHandler.operation, urlGlob, paramType, request, entityDetails, responseChannel, context);
    }

    private static class ResponseGlobHttpRequestHandler implements GlobHttpRequestHandler {
        private final ResponseChannel responseChannel;
        private final HttpContext context;
        private final int code;

        public ResponseGlobHttpRequestHandler(ResponseChannel responseChannel, HttpContext context, int code) {
            this.responseChannel = responseChannel;
            this.context = context;
            this.code = code;
        }

        @Override
        public void callHandler() {
            try {
                responseChannel.sendResponse(SimpleHttpResponse.create(code), null, context);
            } catch (HttpException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void streamEnd(List<? extends Header> trailers) {

        }

        @Override
        public void consumeRequest(ByteBuffer src) {

        }

        @Override
        public void produceResponse(DataStreamChannel channel) throws IOException {
        }

        @Override
        public int availableInResponse() {
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

        }
    }
}
