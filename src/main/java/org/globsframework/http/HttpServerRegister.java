package org.globsframework.http;

import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.globsframework.http.openapi.model.*;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.IsJsonContentAnnotation;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.CommentType;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.Ref;
import org.globsframework.utils.Strings;
import org.globsframework.utils.collections.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HttpServerRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerRegister.class);
    final Map<String, Verb> verbMap = new HashMap<>();
    private String serverInfo;
    private Glob openApiDoc;

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

    public void registerOpenApi() {
        register("/api", null)
                .get(null, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        return CompletableFuture.completedFuture(openApiDoc);
                    }
                });
    }

    public Glob createOpenApiDoc(int port) {
        Map<GlobType, Glob> schemas = new HashMap<>();
        List<Glob> paths = new ArrayList<>();
        for (Map.Entry<String, Verb> stringVerbEntry : verbMap.entrySet()) {
            Verb verb = stringVerbEntry.getValue();
            MutableGlob path = OpenApiPath.TYPE.instantiate();
            paths.add(path);
            path.set(OpenApiPath.name, verb.url);
            for (HttpOperation operation : stringVerbEntry.getValue().operations) {
                MutableGlob desc = OpenApiPathDsc.TYPE.instantiate();
                String comment = operation.getComment();
                if (comment != null) {
                    desc.set(OpenApiPathDsc.description, comment);
                }

                List<Glob> parameters = new ArrayList<>();
                if (verb.queryUrl != null) {
                    for (Field field : verb.queryUrl.getFields()) {
                        OpenApiFieldVisitor apiFieldVisitor = new OpenApiFieldVisitor(schemas);
                        OpenApiFieldVisitor openApiFieldVisitor = field.safeVisit(apiFieldVisitor);
                        parameters.add(OpenApiParameter.TYPE.instantiate()
                                .set(OpenApiParameter.in, "path")
                                .set(OpenApiParameter.name, field.getName())
                                .set(OpenApiParameter.required, true)
                                .set(OpenApiParameter.schema, openApiFieldVisitor.schema));
                    }
                }
                GlobType queryParamType = operation.getQueryParamType();
                if (queryParamType != null) {
                    for (Field field : queryParamType.getFields()) {
                        OpenApiFieldVisitor openApiFieldVisitor = field.safeVisit(new OpenApiFieldVisitor(schemas));
                        parameters.add(OpenApiParameter.TYPE.instantiate()
                                .set(OpenApiParameter.in, "query")
                                .set(OpenApiParameter.name, field.getName())
                                .set(OpenApiParameter.required, true)
                                .set(OpenApiParameter.schema, openApiFieldVisitor.schema));
                    }
                }

                GlobType bodyType = operation.getBodyType();
                if (bodyType != null) {
                    desc.set(OpenApiPathDsc.requestBody, OpenApiRequestBody.TYPE.instantiate()
                            .set(OpenApiRequestBody.content, new Glob[]{OpenApiBodyMimeType.TYPE.instantiate()
                                    .set(OpenApiBodyMimeType.mimeType, "application/json")
                                    .set(OpenApiBodyMimeType.schema, buildSchema(bodyType, schemas))}));
                }
                GlobType returnType = operation.getReturnType();
                if (returnType == null) {
                    desc.set(OpenApiPathDsc.responses, new Glob[]{OpenApiResponses.TYPE
                            .instantiate()
                            .set(OpenApiResponses.description, "None")
                            .set(OpenApiResponses.code, "200")});
                } else {
                    desc.set(OpenApiPathDsc.responses, new Glob[]{OpenApiResponses.TYPE.instantiate()
                            .set(OpenApiResponses.code, "200")
                            .set(OpenApiResponses.description,
                                    returnType.findOptAnnotation(CommentType.UNIQUE_KEY)
                                            .map(CommentType.VALUE).orElse("None"))
                            .set(OpenApiResponses.content, new Glob[]{
                            OpenApiBodyMimeType.TYPE.instantiate()
                                    .set(OpenApiBodyMimeType.mimeType, "application/json")
                                    .set(OpenApiBodyMimeType.schema, buildSchema(returnType, schemas))})
                    });
                }

                desc.set(OpenApiPathDsc.parameters, parameters.toArray(Glob[]::new));
                switch (operation.verb()) {
                    case post:
                        path.set(OpenApiPath.post, desc);
                        break;
                    case put:
                        path.set(OpenApiPath.put, desc);
                        break;
                    case delete:
                        path.set(OpenApiPath.delete, desc);
                        break;
                    case get:
                        path.set(OpenApiPath.get, desc);
                        break;
                }
            }
        }

        return OpenApiType.TYPE.instantiate()
                .set(OpenApiType.openAPIVersion, "3.0.1")
                .set(OpenApiType.info, OpenApiInfo.TYPE.instantiate()
                        .set(OpenApiInfo.description, serverInfo)
                        .set(OpenApiInfo.title, serverInfo)
                        .set(OpenApiInfo.version, "1.0")
                )
                .set(OpenApiType.components, OpenApiComponents.TYPE.instantiate()
                        .set(OpenApiComponents.schemas, schemas.values().toArray(Glob[]::new)))
                .set(OpenApiType.servers, new Glob[]{OpenApiServers.TYPE.instantiate()
                        .set(OpenApiServers.url, "http://localhost:" + port)})
                .set(OpenApiType.paths, paths.toArray(Glob[]::new));
    }

    private MutableGlob buildSchema(GlobType bodyType, Map<GlobType, Glob> schemas) {
        if (!schemas.containsKey(bodyType)) {
            MutableGlob schema = OpenApiSchemaProperty.TYPE.instantiate();
            schemas.put(bodyType, schema);
            schema.set(OpenApiSchemaProperty.name, bodyType.getName());
            schema.set(OpenApiSchemaProperty.type, "object");
            List<Glob> param = new ArrayList<>();
            for (Field field : bodyType.getFields()) {
                param.add(subType(field, schemas));
            }
            schema.set(OpenApiSchemaProperty.properties, param.toArray(Glob[]::new));
        }
        return OpenApiSchemaProperty.TYPE.instantiate()
                .set(OpenApiSchemaProperty.ref, "#/components/schemas/" + bodyType.getName());
    }

    private Glob subType(Field field, Map<GlobType, Glob> schemas) {
        final Ref<Glob> p = new Ref<>();
        field.safeVisit(new FieldVisitor.AbstractWithErrorVisitor() {

            public void visitDouble(DoubleField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "double")
                        .set(OpenApiSchemaProperty.type, "number");
                p.set(instantiate);
            }

            public void visitDoubleArray(DoubleArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "double")
                                        .set(OpenApiSchemaProperty.type, "number"));
                p.set(instantiate);
            }

            public void visitBigDecimal(BigDecimalField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "big-decimal")
                        .set(OpenApiSchemaProperty.type, "string");
                p.set(instantiate);
            }

            public void visitBigDecimalArray(BigDecimalArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "big-decimal")
                                        .set(OpenApiSchemaProperty.type, "string"));
                p.set(instantiate);
            }

            public void visitInteger(IntegerField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "int32")
                        .set(OpenApiSchemaProperty.type, "integer");
                p.set(instantiate);
            }

            public void visitDate(DateField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "date")
                        .set(OpenApiSchemaProperty.type, "string");
                p.set(instantiate);
            }

            public void visitDateTime(DateTimeField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "date-time")
                        .set(OpenApiSchemaProperty.type, "string");
                p.set(instantiate);
            }

            public void visitString(StringField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "string");
                p.set(instantiate);
            }

            public void visitLong(LongField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "int64")
                        .set(OpenApiSchemaProperty.type, "integer");
                p.set(instantiate);
            }

            public void visitLongArray(LongArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "int64")
                                        .set(OpenApiSchemaProperty.type, "integer"));
                p.set(instantiate);
            }

            public void visitIntegerArray(IntegerArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "int32")
                                        .set(OpenApiSchemaProperty.type, "integer"));
                p.set(instantiate);
            }

            public void visitBoolean(BooleanField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "boolean");
                p.set(instantiate);
            }

            public void visitBooleanArray(BooleanArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.type, "boolean"));
                p.set(instantiate);
            }


            public void visitStringArray(StringArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.type, "string"));
                p.set(instantiate);
            }

            public void visitGlob(GlobField field) throws Exception {
                MutableGlob ref = buildSchema(field.getTargetType(), schemas);
                ref.set(OpenApiSchemaProperty.name, field.getName());
//                        .set(OpenApiSchemaProperty.format, "binary")
//                        .set(OpenApiSchemaProperty.type, "object");
                p.set(ref);
            }

            public void visitUnionGlob(GlobUnionField field) throws Exception {
                MutableGlob ref = buildSchema(field.getTargetTypes().iterator().next(), schemas);
                ref.set(OpenApiSchemaProperty.name, field.getName());
//                        .set(OpenApiSchemaProperty.format, "binary")
//                        .set(OpenApiSchemaProperty.type, "object");
                p.set(ref);
            }

            public void visitUnionGlobArray(GlobArrayUnionField field) throws Exception {
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                buildSchema(field.getTargetTypes().iterator().next(), schemas));
                p.set(ref);

            }
            public void visitGlobArray(GlobArrayField field) throws Exception {
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "array")
                        .set(OpenApiSchemaProperty.items,
                                buildSchema(field.getTargetType(), schemas));
                p.set(ref);
            }

            public void visitBlob(BlobField field) throws Exception {
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "binary")
                        .set(OpenApiSchemaProperty.type, "object");
                p.set(ref);
            }
        });
        return p.get();
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
                        .set(HttpAPIDesc.comment, operation.getComment());
                LOGGER.info("Api : " + GSonUtils.encode(logs, false));
            }
        }
        if (Strings.isNotEmpty(serverInfo)) {
            serverBootstrap.setServerInfo(serverInfo);
        }
        return serverBootstrap.create();
    }

    public Pair<HttpServer, Integer> startAndWaitForStartup(ServerBootstrap bootstrap) {
        HttpServer server = init(bootstrap);
        try {
            server.start();
            server.getEndpoint().waitFor();
            InetSocketAddress address = (InetSocketAddress) server.getEndpoint().getAddress();
            int port = address.getPort();
            openApiDoc = createOpenApiDoc(port);
            LOGGER.info("OpenApi doc : " + GSonUtils.encode(openApiDoc, false));
            return Pair.makePair(server, port);
        } catch (Exception e) {
            String message = "Fail to start server" + serverInfo;
            LOGGER.error(message);
            throw new RuntimeException(message, e);
        }
    }

    public interface OperationInfo {
        OperationInfo declareReturnType(GlobType globType);

        OperationInfo comment(String comment);

        void addHeader(String name, String value);
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

    private class OpenApiFieldVisitor extends FieldVisitor.AbstractWithErrorVisitor {
        private Glob schema;
        private Map<GlobType, Glob> schemas;

        public OpenApiFieldVisitor(Map<GlobType, Glob> schemas) {
            this.schemas = schemas;
        }


        public void visitInteger(IntegerField field) throws Exception {
            createSchema("integer", "int32");
        }

        private void createSchema(String type, String format) {
            schema = create(type, format);
        }

        private MutableGlob create(String type, String format) {
            MutableGlob set = OpenApiSchemaProperty.TYPE.instantiate()
                    .set(OpenApiSchemaProperty.type, type);
            if (format != null) {
                set.set(OpenApiSchemaProperty.format, format);
            }
            return set;
        }

        public void visitDouble(DoubleField field) throws Exception {
            createSchema("number", "double");
        }

        public void visitString(StringField field) throws Exception {
            createSchema("string", null);
        }

        public void visitBoolean(BooleanField field) throws Exception {
            createSchema("boolean", null);
        }

        public void visitLong(LongField field) throws Exception {
            createSchema("integer", "int64");
        }

        public void visitStringArray(StringArrayField field) throws Exception {
            createArray("string", null);
        }

        public void visitDoubleArray(DoubleArrayField field) throws Exception {
            createArray("number", "double");
        }

        public void visitIntegerArray(IntegerArrayField field) throws Exception {
            createArray("integer", "int32");
        }

        public void visitLongArray(LongArrayField field) throws Exception {
            createArray("integer", "int64");
        }

        public void visitDate(DateField field) throws Exception {
            createSchema("string", "date");
        }

        public void visitDateTime(DateTimeField field) throws Exception {
            createSchema("string", "date-time");
        }

        public void visitBooleanArray(BooleanArrayField field) throws Exception {
            createArray("boolean", null);
        }

        public void visitBigDecimal(BigDecimalField field) throws Exception {
            createSchema("string", "big-decimal");
        }

        public void visitBigDecimalArray(BigDecimalArrayField field) throws Exception {
            createArray("string", "big-decimal");
        }

        private void createArray(String type, String format) {
            schema = OpenApiSchemaProperty.TYPE.instantiate()
                    .set(OpenApiSchemaProperty.type, "array")
                    .set(OpenApiSchemaProperty.items, create(type, format));
        }

        public void visitGlob(GlobField field) throws Exception {
            schema = buildSchema(field.getGlobType(), schemas);
        }

        public void visitGlobArray(GlobArrayField field) throws Exception {
            schema = OpenApiSchemaProperty.TYPE.instantiate()
                    .set(OpenApiSchemaProperty.type, "array")
                    .set(OpenApiSchemaProperty.items, buildSchema(field.getTargetType(), schemas));

        }
    }

    public class Verb {
        private final String url;
        private final GlobType queryUrl;
        private boolean gzipCompress = false;
        private List<HttpOperation> operations = new ArrayList<>();
        private final Map<String, String> headers = new HashMap<>();


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

        public OperationInfo get(GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.get, null, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo post(GlobType bodyParam, GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.post, bodyParam, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo put(GlobType bodyParam, GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.put, bodyParam, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo delete(GlobType paramType, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.delete, null, paramType, httpTreatment);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        HttpReceiver complete() {
            DefaultHttpReceiver defaultHttpReceiver = new DefaultHttpReceiver(url, queryUrl, operations.toArray(new HttpOperation[0]));
            headers.forEach(defaultHttpReceiver::addHeader);
            return defaultHttpReceiver;
        }

        private class DefaultOperationInfo implements OperationInfo {
            private final DefaultHttpOperation operation;

            public DefaultOperationInfo(DefaultHttpOperation operation) {
                this.operation = operation;
            }

            public OperationInfo declareReturnType(GlobType type) {
                operation.withReturnType(type);
                return this;
            }

            public OperationInfo comment(String comment) {
                operation.withComment(comment);
                return this;
            }

            public void addHeader(String name, String value) {
                operation.addHeader(name, value);
            }
        }
    }
}
