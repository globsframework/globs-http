package org.globsframework.http;

import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.IsJsonContentAnnotation;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServerRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerRegister.class);
    final Map<String, Verb> verbMap = new HashMap<>();
    private String serverInfo;

    public HttpServerRegister(String serverInfo) {
        this.serverInfo = serverInfo;
    }

    public Verb register(String url, GlobType queryUrl) {
        Verb current = verbMap.get(url);
        if (current == null) {
            Verb verb = new Verb(url, queryUrl);
            verbMap.put(url, verb);
            return verb;
        } else {
            if (current.queryUrl != queryUrl) {
                throw new RuntimeException("Same query Type is expected for same url on different verb (" + url + ")");
            }
        }
        return current;
    }

    public HttpServer init(ServerBootstrap serverBootstrap) {
        for (Map.Entry<String, Verb> stringVerbEntry : verbMap.entrySet()) {
            Verb verb = stringVerbEntry.getValue();
            GlobHttpRequestHandler globHttpRequestHandler = new GlobHttpRequestHandler(verb.complete(), verb.gzipCompress);
            serverBootstrap.registerHandler(globHttpRequestHandler.createRegExp(), globHttpRequestHandler);
            for (HttpOperation operation : stringVerbEntry.getValue().operations) {

                MutableGlob logs = HttpAPIDesc.TYPE.instantiate()
                        .set(HttpAPIDesc.serverName, serverInfo)
                        .set(HttpAPIDesc.url, stringVerbEntry.getKey())
                        .set(HttpAPIDesc.queryParam, GSonUtils.encodeGlobType(operation.getQueryParamType()))
                        .set(HttpAPIDesc.body, GSonUtils.encodeGlobType(operation.getBodyType()))
                        .set(HttpAPIDesc.returnType, GSonUtils.encodeGlobType(operation.getReturnType()))
                        .set(HttpAPIDesc.comment, operation.getComment())
                        ;
                LOGGER.info("Api : " + GSonUtils.encode(logs, false));
            }
        }
        if (Strings.isNotEmpty(serverInfo)) {
            serverBootstrap.setServerInfo(serverInfo);
        }
        return serverBootstrap.create();
    }

    public interface ReturnType {
        ReturnType add(GlobType globType);

        ReturnType comment(String comment);
    }

    public class Verb {
        private final String url;
        private final GlobType queryUrl;
        private boolean gzipCompress = false;
        private List<HttpOperation> operations = new ArrayList<>();

        public Verb(String url, GlobType queryUrl) {
            this.url = url;
            this.queryUrl = queryUrl;
        }

        public Verb setGzipCompress() {
            this.gzipCompress = true;
            return this;
        }

        public Verb setGzipCompress(boolean gzipCompress) {
            this.gzipCompress = true;
            return this;
        }

        public ReturnType get(GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.get, null, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultReturnType(operation);
        }

        public ReturnType post(GlobType bodyParam, GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.post, bodyParam, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultReturnType(operation);
        }

        public ReturnType put(GlobType bodyParam, GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.put, bodyParam, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultReturnType(operation);
        }

        public ReturnType delete(GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.delete, null, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultReturnType(operation);
        }

        HttpReceiver complete() {
            return new DefaultHttpReceiver(url, queryUrl, operations.toArray(new HttpOperation[0]));
        }

        private class DefaultReturnType implements ReturnType {
            private final DefaultHttpOperation operation;

            public DefaultReturnType(DefaultHttpOperation operation) {
                this.operation = operation;
            }

            public ReturnType add(GlobType type) {
                operation.withReturnType(type);
                return this;
            }

            public ReturnType comment(String comment) {
                operation.withComment(comment);
                return this;
            }
        }
    }


    public static class HttpAPIDesc {
        public static GlobType TYPE;

        public static StringField serverName;

        public static StringField url;

        public static StringField verb;

        @IsJsonContentAnnotation
        public static StringField queryParam;

        @IsJsonContentAnnotation
        public static StringField body;

        @IsJsonContentAnnotation
        public static StringField returnType;

        public static StringField comment;

        static {
            GlobTypeLoaderFactory.create(HttpAPIDesc.class).load();
        }

    }
}
