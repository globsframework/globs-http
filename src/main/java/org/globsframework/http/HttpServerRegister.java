package org.globsframework.http;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Comment;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.Ref;
import org.globsframework.core.utils.Strings;
import org.globsframework.http.openapi.model.*;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.IsJsonContent;
import org.globsframework.json.annottations.IsJsonContent_;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class HttpServerRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerRegister.class);

    private static final String DOUBLE_STR = "double";
    private static final String NUMBER_STR = "number";
    private static final String ARRAY_STR = "array";
    private static final String BIG_DECIMAL_STR = "big-decimal";
    private static final String STRING_STR = "string";

    private final Map<String, Verb> verbMap = new LinkedHashMap<>();
    private final String serverInfo;
    private Glob openApiDoc;
    private InterceptBuilder interceptBuilder = InterceptBuilder.NULL;

    public HttpServerRegister(String serverInfo) {
        this.serverInfo = serverInfo;
    }

    public void addRequestDecorator(InterceptBuilder interceptBuilder) {
        if (this.interceptBuilder == InterceptBuilder.NULL) {
            this.interceptBuilder = interceptBuilder;
        } else {
            this.interceptBuilder = new AncapsulateInterceptBuilder(this.interceptBuilder, interceptBuilder);
        }
    }

    public Verb register(String url, GlobType pathParameters) {
        Verb current = verbMap.get(url);
        if (current == null) {
            Verb verb = new Verb(url, pathParameters);
            verbMap.put(url, verb);
            return verb;
        } else if (current.pathParameters != pathParameters) {
            throw new RuntimeException(serverInfo + ": Same query Type is expected for same url on different verb (" + url + ")");
        }
        return current;
    }

    public void registerOpenApi() {

        register("/api", null)
                .get(GetOpenApiParamType.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob pathParameters, Glob queryParameters) throws Exception {
                        String scope = queryParameters == null ? "" : queryParameters.get(GetOpenApiParamType.scope);
                        if (Strings.isNullOrEmpty(scope)) {
                            return CompletableFuture.completedFuture(openApiDoc);
                        }
                        return CompletableFuture.completedFuture(createOpenApiDocByTags(scope));
                    }
                }); //.declareReturnType(OpenApiType.TYPE);
    }

    public Glob createOpenApiDocByTags(String tag) {
        List<Glob> paths = new ArrayList<>();
        Arrays.stream(openApiDoc.getOrEmpty(OpenApiType.paths)).forEach(path -> {
            boolean isPathSelected =
                    hasSelectedTag(path, OpenApiPath.get, tag) ||
                            hasSelectedTag(path, OpenApiPath.put, tag) ||
                            hasSelectedTag(path, OpenApiPath.post, tag) ||
                            hasSelectedTag(path, OpenApiPath.delete, tag) ||
                            hasSelectedTag(path, OpenApiPath.patch, tag);
            if (isPathSelected) {
                paths.add(path);
            }
        });


        return openApiDoc.duplicate().set(OpenApiType.paths, paths.toArray(new Glob[0]));
    }

    private boolean hasSelectedTag(Glob path, GlobField field, String targetScope) {
        Glob pathDescription = path.get(field);
        if (pathDescription == null) {
            return false;
        }

        String[] currentScopes = pathDescription.getOrEmpty(OpenApiPathDsc.tags);
        return Arrays.asList(currentScopes).contains(targetScope);
    }

    public Glob createOpenApiDoc(int port) {
        Map<GlobType, Glob> schemas = new LinkedHashMap<>();
        List<Glob> paths = new ArrayList<>();
        for (Map.Entry<String, Verb> stringVerbEntry : verbMap.entrySet()) {
            createVerbDoc(schemas, paths, stringVerbEntry);
        }

        return OpenApiType.TYPE.instantiate()
                .set(OpenApiType.openAPIVersion, "3.0.1")
                .set(OpenApiType.info, OpenApiInfo.TYPE.instantiate()
                        .set(OpenApiInfo.description, serverInfo)
                        .set(OpenApiInfo.title, serverInfo)
                        .set(OpenApiInfo.version, "1.0")
                )
                .set(OpenApiType.components, OpenApiComponents.TYPE.instantiate()
                        .set(OpenApiComponents.schemas, schemas.values().toArray(new Glob[0])))
                .set(OpenApiType.servers, new Glob[]{OpenApiServers.TYPE.instantiate()
                        .set(OpenApiServers.url, "http://localhost:" + port)})
                .set(OpenApiType.paths, paths.toArray(new Glob[0]));
    }

    private void createVerbDoc(Map<GlobType, Glob> schemas, List<Glob> paths, Map.Entry<String, Verb> stringVerbEntry) {
        Verb verb = stringVerbEntry.getValue();
        MutableGlob path = OpenApiPath.TYPE.instantiate();
        paths.add(path);
        path.set(OpenApiPath.name, verb.url);
        for (HttpOperation operation : stringVerbEntry.getValue().operations) {
            createOperationDoc(schemas, verb, path, operation);

        }
    }

    private void createOperationDoc(Map<GlobType, Glob> schemas, Verb verb, MutableGlob path, HttpOperation operation) {
        MutableGlob desc = OpenApiPathDsc.TYPE.instantiate();
        setOperationComment(operation, desc);
        List<Glob> parameters = getOperationPathParameters(schemas, verb);
        addOperationQueryParameters(schemas, operation, parameters);
        setOperationRequestBody(schemas, operation, desc);
        setOperationTags(operation, desc);
        setOperationReturnType(schemas, operation, desc);
        setPathDescription(path, operation, desc, parameters);
    }

    private void setOperationReturnType(Map<GlobType, Glob> schemas, HttpOperation operation, MutableGlob desc) {
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
                            returnType.findOptAnnotation(Comment.UNIQUE_KEY)
                                    .map(Comment.VALUE).orElse("None"))
                    .set(OpenApiResponses.content, new Glob[]{
                    OpenApiBodyMimeType.TYPE.instantiate()
                            .set(OpenApiBodyMimeType.mimeType, "application/json")
                            .set(OpenApiBodyMimeType.schema, buildSchema(returnType, schemas))})
            });
        }
    }

    private void setOperationComment(HttpOperation operation, MutableGlob desc) {
        String comment = operation.getComment();
        if (comment != null) {
            desc.set(OpenApiPathDsc.description, comment);
        }
    }

    private void setOperationTags(HttpOperation operation, MutableGlob desc) {
        String[] tags = operation.getTags();
        if (tags != null) {
            desc.set(OpenApiPathDsc.tags, tags);
        }
    }

    private void setOperationRequestBody(Map<GlobType, Glob> schemas, HttpOperation operation, MutableGlob desc) {
        GlobType bodyType = operation.getBodyType();
        if (bodyType != null) {
            desc.set(OpenApiPathDsc.requestBody, OpenApiRequestBody.TYPE.instantiate()
                    .set(OpenApiRequestBody.content, new Glob[]{OpenApiBodyMimeType.TYPE.instantiate()
                            .set(OpenApiBodyMimeType.mimeType, "application/json")
                            .set(OpenApiBodyMimeType.schema, buildSchema(bodyType, schemas))}));
        }
    }

    private void addOperationQueryParameters(Map<GlobType, Glob> schemas, HttpOperation operation, List<Glob> parameters) {
        GlobType queryParamType = operation.getQueryParamType();
        if (queryParamType != null) {
            for (Field field : queryParamType.getFields()) {
                OpenApiFieldVisitor openApiFieldVisitor = field.safeAccept(new OpenApiFieldVisitor(schemas));
                parameters.add(OpenApiParameter.TYPE.instantiate()
                        .set(OpenApiParameter.in, "query")
                        .set(OpenApiParameter.name, field.getName())
                        .set(OpenApiParameter.required, true)
                        .set(OpenApiParameter.schema, openApiFieldVisitor.schema));
            }
        }
    }

    private List<Glob> getOperationPathParameters(Map<GlobType, Glob> schemas, Verb verb) {
        List<Glob> parameters = new ArrayList<>();
        if (verb.pathParameters != null) {
            for (Field field : verb.pathParameters.getFields()) {
                OpenApiFieldVisitor apiFieldVisitor = new OpenApiFieldVisitor(schemas);
                OpenApiFieldVisitor openApiFieldVisitor = field.safeAccept(apiFieldVisitor);
                parameters.add(OpenApiParameter.TYPE.instantiate()
                        .set(OpenApiParameter.in, "path")
                        .set(OpenApiParameter.name, field.getName())
                        .set(OpenApiParameter.required, true)
                        .set(OpenApiParameter.schema, openApiFieldVisitor.schema));
            }
        }
        return parameters;
    }

    private void setPathDescription(MutableGlob path, HttpOperation operation, MutableGlob desc, List<Glob> parameters) {
        desc.set(OpenApiPathDsc.parameters, parameters.toArray(new Glob[0]));
        switch (operation.verb()) {
            case post:
                path.set(OpenApiPath.post, desc);
                break;
            case put:
                path.set(OpenApiPath.put, desc);
                break;
            case patch:
                path.set(OpenApiPath.patch, desc);
                break;
            case delete:
                path.set(OpenApiPath.delete, desc);
                break;
            case get:
                path.set(OpenApiPath.get, desc);
                break;
        }
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
            schema.set(OpenApiSchemaProperty.properties, param.toArray(new Glob[0]));
        }
        return OpenApiSchemaProperty.TYPE.instantiate()
                .set(OpenApiSchemaProperty.ref, "#/components/schemas/" + bodyType.getName());
    }

    private Glob subType(Field field, Map<GlobType, Glob> schemas) {
        final Ref<Glob> p = new Ref<>();
        field.safeAccept(new FieldVisitor.AbstractWithErrorVisitor() {

            @Override
            public void visitDouble(DoubleField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, DOUBLE_STR)
                        .set(OpenApiSchemaProperty.type, NUMBER_STR);
                p.set(instantiate);
            }

            @Override
            public void visitDoubleArray(DoubleArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, DOUBLE_STR)
                                        .set(OpenApiSchemaProperty.type, NUMBER_STR));
                p.set(instantiate);
            }

            @Override
            public void visitBigDecimal(BigDecimalField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, BIG_DECIMAL_STR)
                        .set(OpenApiSchemaProperty.type, STRING_STR);
                p.set(instantiate);
            }

            @Override
            public void visitBigDecimalArray(BigDecimalArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, BIG_DECIMAL_STR)
                                        .set(OpenApiSchemaProperty.type, STRING_STR));
                p.set(instantiate);
            }

            @Override
            public void visitInteger(IntegerField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "int32")
                        .set(OpenApiSchemaProperty.type, "integer");
                p.set(instantiate);
            }

            @Override
            public void visitDate(DateField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "date")
                        .set(OpenApiSchemaProperty.type, STRING_STR);
                p.set(instantiate);
            }

            @Override
            public void visitDateTime(DateTimeField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "date-time")
                        .set(OpenApiSchemaProperty.type, STRING_STR);
                p.set(instantiate);
            }

            @Override
            public void visitString(StringField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, STRING_STR);
                p.set(instantiate);
            }

            @Override
            public void visitLong(LongField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "int64")
                        .set(OpenApiSchemaProperty.type, "integer");
                p.set(instantiate);
            }

            @Override
            public void visitLongArray(LongArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "int64")
                                        .set(OpenApiSchemaProperty.type, "integer"));
                p.set(instantiate);
            }

            @Override
            public void visitIntegerArray(IntegerArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.format, "int32")
                                        .set(OpenApiSchemaProperty.type, "integer"));
                p.set(instantiate);
            }

            @Override
            public void visitBoolean(BooleanField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, "boolean");
                p.set(instantiate);
            }

            @Override
            public void visitBooleanArray(BooleanArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.type, "boolean"));
                p.set(instantiate);
            }

            @Override
            public void visitStringArray(StringArrayField field) throws Exception {
                MutableGlob instantiate = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                OpenApiSchemaProperty.TYPE.instantiate()
                                        .set(OpenApiSchemaProperty.type, STRING_STR));
                p.set(instantiate);
            }

            @Override
            public void visitGlob(GlobField field) throws Exception {
                MutableGlob ref = buildSchema(field.getTargetType(), schemas);
                ref.set(OpenApiSchemaProperty.name, field.getName());
//                        .set(OpenApiSchemaProperty.format, "binary")
//                        .set(OpenApiSchemaProperty.type, "object");
                p.set(ref);
            }

            @Override
            public void visitUnionGlob(GlobUnionField field) throws Exception {
                List<Glob> sub = extractUnion(field.getTargetTypes());

                MutableGlob schema = OpenApiSchemaProperty.TYPE.instantiate();
                schema.set(OpenApiSchemaProperty.name, field.getName());
                schema.set(OpenApiSchemaProperty.anyOf, sub.toArray(new Glob[0]));
//                ref.set(OpenApiSchemaProperty.name, field.getName());
//                        .set(OpenApiSchemaProperty.format, "binary")
//                        .set(OpenApiSchemaProperty.type, "object");
                p.set(schema);
            }

            private List<Glob> extractUnion(Collection<GlobType> targetTypes) {
                List<Glob> sub = new ArrayList<>();
                for (GlobType targetType : targetTypes) {
                    String name = targetType.getName() + "_union";
                    Optional<Map.Entry<GlobType, Glob>> first = schemas.entrySet().stream().filter(e -> e.getKey().getName().equals(name)).findFirst();
                    sub.add(first.map(entry ->
                                    OpenApiSchemaProperty.TYPE.instantiate()
                                            .set(OpenApiSchemaProperty.ref, "#/components/schemas/" + entry.getKey().getName()))
                            .orElseGet(() -> buildSchema(
                                    GlobTypeBuilderFactory.create(name)
                                            .addGlobField(targetType.getName(), Collections.emptyList(), targetType).get(), schemas)));
                }
                return sub;
            }

            public void visitUnionGlobArray(GlobArrayUnionField field) throws Exception {
                List<Glob> sub = extractUnion(field.getTargetTypes());
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items, OpenApiSchemaProperty.TYPE.instantiate()
                                .set(OpenApiSchemaProperty.anyOf, sub.toArray(new Glob[0]))
                        );
                p.set(ref);
            }

            public void visitGlobArray(GlobArrayField field) throws Exception {
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.type, ARRAY_STR)
                        .set(OpenApiSchemaProperty.items,
                                buildSchema(field.getTargetType(), schemas));
                p.set(ref);
            }

            @Override
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


    interface BootStratServer {

        void setRequestRouter(final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter);

        HttpAsyncServer create();
    }

    public HttpAsyncServer init(BootStratServer serverBootstrap) {

        RequestDispatcher requestDispatcher = new RequestDispatcher(serverInfo);
        for (Map.Entry<String, Verb> stringVerbEntry : verbMap.entrySet()) {
            Verb verb = stringVerbEntry.getValue();
            GlobHttpRequestHandlerBuilder globHttpRequestHandler = new GlobHttpRequestHandlerBuilder(serverInfo, verb.complete());
            Collection<String> path = globHttpRequestHandler.createRegExp();
            requestDispatcher.register(path, globHttpRequestHandler);
            for (HttpOperation operation : stringVerbEntry.getValue().operations) {
                MutableGlob logs = HttpAPIDesc.TYPE.instantiate()
                        .set(HttpAPIDesc.serverName, serverInfo)
                        .set(HttpAPIDesc.url, stringVerbEntry.getKey())
                        .set(HttpAPIDesc.queryParam, GSonUtils.encodeGlobType(operation.getQueryParamType()))
                        .set(HttpAPIDesc.body, GSonUtils.encodeGlobType(operation.getBodyType()))
                        .set(HttpAPIDesc.returnType, GSonUtils.encodeGlobType(operation.getReturnType()))
                        .set(HttpAPIDesc.comment, operation.getComment());
                LOGGER.info(serverInfo + " Api : {}", GSonUtils.encode(logs, false));
            }
        }
//        if (Strings.isNotEmpty(serverInfo)) {
//            serverBootstrap.setServerInfo(serverInfo);
//        }
        serverBootstrap.setRequestRouter((request, context) ->
                () -> new HttpRequestHttpAsyncServerExchangeTree(requestDispatcher, request, context));
        return serverBootstrap.create();
    }

    public static class Server {
        private final HttpAsyncServer server;
        private final int port;

        public Server(HttpAsyncServer server, int port) {
            this.server = server;
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public HttpAsyncServer getServer() {
            return server;
        }
    }

    public Server startAndWaitForStartup(H2ServerBootstrap bootstrap, int wantedPort) {
        HttpAsyncServer server = init(new BootStratServer() {
            @Override
            public void setRequestRouter(HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter) {
                bootstrap.setRequestRouter(requestRouter);
            }

            @Override
            public HttpAsyncServer create() {
                return bootstrap.create();
            }
        });
        return initHttpServer(wantedPort, server);
    }

    public Server startAndWaitForStartup(AsyncServerBootstrap bootstrap, int wantedPort) {
        HttpAsyncServer server = init(new BootStratServer() {
            @Override
            public void setRequestRouter(HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter) {
                bootstrap.setRequestRouter(requestRouter);
            }

            @Override
            public HttpAsyncServer create() {
                return bootstrap.create();
            }
        });
        return initHttpServer(wantedPort, server);
    }

    private Server initHttpServer(int wantedPort, HttpAsyncServer server) {
        try {
            server.start();
            Future<ListenerEndpoint> listen = server.listen(new InetSocketAddress(wantedPort), URIScheme.HTTP);
            ListenerEndpoint listenerEndpoint = listen.get();
            InetSocketAddress address = (InetSocketAddress) listenerEndpoint.getAddress();
            int port = address.getPort();
            openApiDoc = createOpenApiDoc(port);
            LOGGER.info(serverInfo + " OpenApi doc : {}", GSonUtils.encode(openApiDoc, false));
            return new Server(server, port);
        } catch (Exception e) {
            String message = serverInfo + " Fail to start server" + serverInfo;
            LOGGER.error(message);
            throw new RuntimeException(message, e);
        }
    }

    public interface InterceptBuilder {
        InterceptBuilder NULL = httpTreatment -> httpTreatment;

        default HttpTreatmentWithHeader create(HttpTreatment httpTreatment) {
            return (body, url, queryParameters, headerType) -> httpTreatment.consume(body, url, queryParameters);
        }

        HttpTreatmentWithHeader create(HttpTreatmentWithHeader httpTreatment);
    }

    public interface OperationInfo {

        OperationInfo withSensitiveData(boolean hasSensitiveData);

        OperationInfo declareReturnType(GlobType globType);

        OperationInfo withHeaderType(GlobType headerType);

        OperationInfo declareTags(String[] tags);

        OperationInfo comment(String comment);

        OperationInfo withExecutor(Executor executor);

        void addHeader(String name, String value);
    }

    public static class HttpAPIDesc {
        public static final GlobType TYPE;

        public static final StringField serverName;

        public static final StringField url;

        public static final StringField verb;

        @IsJsonContent_
        public static final StringField queryParam;

        @IsJsonContent_
        public static final StringField body;

        @IsJsonContent_
        public static final StringField returnType;

        public static final StringField comment;

        static {
            GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("HttpAPIDesc");
            TYPE = globTypeBuilder.unCompleteType();
            serverName = globTypeBuilder.declareStringField("serverName");
            url = globTypeBuilder.declareStringField("url");
            verb = globTypeBuilder.declareStringField("verb");
            queryParam = globTypeBuilder.declareStringField("queryParam", IsJsonContent.UNIQUE_GLOB);
            body = globTypeBuilder.declareStringField("body", IsJsonContent.UNIQUE_GLOB);
            returnType = globTypeBuilder.declareStringField("returnType", IsJsonContent.UNIQUE_GLOB);
            comment = globTypeBuilder.declareStringField("comment");
            globTypeBuilder.complete();
//            GlobTypeLoaderFactory.create(HttpAPIDesc.class).load();
        }

    }

    static class StrNode {
        private final String serverInfo;
        private SubStrNode[] subStrNodes = new SubStrNode[0];
        private SubStrNode[] subWithWildCard = new SubStrNode[0];

        StrNode(String serverInfo) {
            this.serverInfo = serverInfo;
        }

        public GlobHttpRequestHandlerFactory createRequestHandler(String[] path, String method, String paramStr, boolean hasBody) {
            for (SubStrNode subStrNode : this.subStrNodes) {
                if (subStrNode.match(path)) {
                    return subStrNode.httpRequestHandlerBuilder.create(path, method, paramStr, hasBody);
                }
            }
            return null;
        }

        public GlobHttpRequestHandlerFactory findAndCreateRequestHandler(String[] path, String method, String paramStr, boolean hasBody) {
            for (SubStrNode subStrNode : this.subWithWildCard) {
                if (subStrNode.match(path)) {
                    return subStrNode.httpRequestHandlerBuilder.create(path, method, paramStr, hasBody);
                }
            }
            return null;
        }

        public void register(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
            subStrNodes = Arrays.copyOf(subStrNodes, subStrNodes.length + 1);
            subStrNodes[subStrNodes.length - 1] = new SubStrNode(path, globHttpRequestHandler);
        }

        public void registerWildcard(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
            subWithWildCard = Arrays.copyOf(subWithWildCard, subWithWildCard.length + 1);
            subWithWildCard[subWithWildCard.length - 1] = new SubStrNode(path, globHttpRequestHandler);
        }
    }

    static class SubStrNode {
        private final String[] path;
        private final GlobHttpRequestHandlerBuilder httpRequestHandlerBuilder;

        public SubStrNode(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
            this.path = path.toArray(new String[0]);
            this.httpRequestHandlerBuilder = globHttpRequestHandler;
        }

        boolean match(String[] path) {
            String[] strings = this.path;
            for (int i = 0, stringsLength = strings.length; i < stringsLength; i++) {
                String s = strings[i];
                if (s != null) {
                    if (!s.equals(path[i])) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static class AncapsulateInterceptBuilder implements InterceptBuilder {
        private final InterceptBuilder interceptBuilder;
        private final InterceptBuilder builder;

        public AncapsulateInterceptBuilder(InterceptBuilder interceptBuilder, InterceptBuilder builder) {
            this.interceptBuilder = interceptBuilder;
            this.builder = builder;
        }

        public HttpTreatmentWithHeader create(HttpTreatment httpTreatment) {
            return interceptBuilder.create(builder.create(httpTreatment));
        }

        public HttpTreatmentWithHeader create(HttpTreatmentWithHeader httpTreatment) {
            return interceptBuilder.create(builder.create(httpTreatment));
        }
    }

    private class OpenApiFieldVisitor extends FieldVisitor.AbstractWithErrorVisitor {
        private Glob schema;
        private Map<GlobType, Glob> schemas;

        public OpenApiFieldVisitor(Map<GlobType, Glob> schemas) {
            this.schemas = schemas;
        }

        @Override
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

        @Override
        public void visitDouble(DoubleField field) throws Exception {
            createSchema(NUMBER_STR, DOUBLE_STR);
        }

        @Override
        public void visitString(StringField field) throws Exception {
            createSchema(STRING_STR, null);
        }

        @Override
        public void visitBoolean(BooleanField field) throws Exception {
            createSchema("boolean", null);
        }

        @Override
        public void visitLong(LongField field) throws Exception {
            createSchema("integer", "int64");
        }

        @Override
        public void visitStringArray(StringArrayField field) throws Exception {
            createArray(STRING_STR, null);
        }

        @Override
        public void visitDoubleArray(DoubleArrayField field) throws Exception {
            createArray(NUMBER_STR, DOUBLE_STR);
        }

        @Override
        public void visitIntegerArray(IntegerArrayField field) throws Exception {
            createArray("integer", "int32");
        }

        @Override
        public void visitLongArray(LongArrayField field) throws Exception {
            createArray("integer", "int64");
        }

        @Override
        public void visitDate(DateField field) throws Exception {
            createSchema(STRING_STR, "date");
        }

        @Override
        public void visitDateTime(DateTimeField field) throws Exception {
            createSchema(STRING_STR, "date-time");
        }

        @Override
        public void visitBooleanArray(BooleanArrayField field) throws Exception {
            createArray("boolean", null);
        }

        @Override
        public void visitBigDecimal(BigDecimalField field) throws Exception {
            createSchema(STRING_STR, BIG_DECIMAL_STR);
        }

        @Override
        public void visitBigDecimalArray(BigDecimalArrayField field) throws Exception {
            createArray(STRING_STR, BIG_DECIMAL_STR);
        }

        private void createArray(String type, String format) {
            schema = OpenApiSchemaProperty.TYPE.instantiate()
                    .set(OpenApiSchemaProperty.type, ARRAY_STR)
                    .set(OpenApiSchemaProperty.items, create(type, format));
        }

        @Override
        public void visitGlob(GlobField field) throws Exception {
            schema = buildSchema(field.getGlobType(), schemas);
        }

        @Override
        public void visitGlobArray(GlobArrayField field) throws Exception {
            schema = OpenApiSchemaProperty.TYPE.instantiate()
                    .set(OpenApiSchemaProperty.type, ARRAY_STR)
                    .set(OpenApiSchemaProperty.items, buildSchema(field.getTargetType(), schemas));

        }
    }

    public class Verb {
        private final String url;
        private final GlobType pathParameters;
        private final Map<String, String> headers = new LinkedHashMap<>();
        // TODO: these are scoped
        private List<HttpOperation> operations = new ArrayList<>();


        public Verb(String url, GlobType pathParameters) {
            this.url = url;
            this.pathParameters = pathParameters;
        }

        public OperationInfo get(GlobType queryParameters, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.get, null, queryParameters, interceptBuilder.create(httpTreatment));
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo get(GlobType queryParameters, GlobType headerType, HttpTreatmentWithHeader httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.get, null, queryParameters, interceptBuilder.create(httpTreatment));
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo getBin(GlobType queryParameters, GlobType headerType, HttpDataTreatmentWithHeader httpTreatment) {
            DefaultHttpDataOperation operation = new DefaultHttpDataOperation(HttpOp.get, null, queryParameters, httpTreatment);
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo post(GlobType bodyParam, GlobType queryParameters, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.post, bodyParam, queryParameters, interceptBuilder.create(httpTreatment));
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo post(GlobType bodyParam, GlobType paramType, GlobType headerType, HttpTreatmentWithHeader httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.post, bodyParam, paramType, interceptBuilder.create(httpTreatment));
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo postBin(GlobType queryParameters, GlobType headerType, HttpDataTreatmentWithHeader httpTreatment) {
            MutableHttpDataOperation operation = new DefaultHttpDataOperation(HttpOp.post, null, queryParameters, httpTreatment);
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo put(GlobType bodyParam, GlobType queryParameters, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.put, bodyParam, queryParameters, interceptBuilder.create(httpTreatment));
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo put(GlobType bodyParam, GlobType queryParameters, GlobType headerType, HttpTreatmentWithHeader httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.put, bodyParam, queryParameters, interceptBuilder.create(httpTreatment));
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo patch(GlobType bodyParam, GlobType queryParameters, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.patch, bodyParam, queryParameters, interceptBuilder.create(httpTreatment));
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo patch(GlobType bodyParam, GlobType queryParameters, GlobType headerType, HttpTreatmentWithHeader httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.patch, bodyParam, queryParameters, interceptBuilder.create(httpTreatment));
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo delete(GlobType queryParameters, HttpTreatment httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.delete, null, queryParameters, interceptBuilder.create(httpTreatment));
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public OperationInfo delete(GlobType queryParameters, GlobType headerType, HttpTreatmentWithHeader httpTreatment) {
            DefaultHttpOperation operation = new DefaultHttpOperation(HttpOp.delete, null, queryParameters, interceptBuilder.create(httpTreatment));
            operation.withHeader(headerType);
            operations.add(operation);
            return new DefaultOperationInfo(operation);
        }

        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        HttpReceiver complete() {
            DefaultHttpReceiver defaultHttpReceiver = new DefaultHttpReceiver(url, pathParameters, operations.toArray(new HttpOperation[0]));
            headers.forEach(defaultHttpReceiver::addHeader);
            return defaultHttpReceiver;
        }

        private class DefaultOperationInfo implements OperationInfo {
            private final MutableHttpDataOperation operation;

            public DefaultOperationInfo(MutableHttpDataOperation operation) {
                this.operation = operation;
            }

            public OperationInfo withSensitiveData(boolean hasSensitiveData) {
                operation.withSensitiveData(hasSensitiveData);
                return this;
            }

            public OperationInfo declareReturnType(GlobType type) {
                operation.withReturnType(type);
                return this;
            }

            public OperationInfo withHeaderType(GlobType headerType) {
                operation.withHeader(headerType);
                return this;
            }

            public OperationInfo declareTags(String[] tags) {
                operation.withTags(tags);
                return this;
            }

            public OperationInfo comment(String comment) {
                operation.withComment(comment);
                return this;
            }

            public OperationInfo withExecutor(Executor executor) {
                operation.withExecutor(executor);
                return this;
            }

            public void addHeader(String name, String value) {
                operation.addHeader(name, value);
            }
        }
    }
}
