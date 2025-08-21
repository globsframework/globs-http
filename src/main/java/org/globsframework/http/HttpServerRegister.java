package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.utils.Strings;
import org.globsframework.http.openapi.model.GetOpenApiParamType;
import org.globsframework.http.openapi.model.GlobOpenApi;
import org.globsframework.json.annottations.IsJsonContent;
import org.globsframework.json.annottations.IsJsonContent_;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class HttpServerRegister {
    public final Map<String, Verb> verbMap = new LinkedHashMap<>();
    public final String serverInfo;
    public InterceptBuilder interceptBuilder = InterceptBuilder.NULL;

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

    public void registerOpenApi(GlobOpenApi openApiDoc) {
        register("/api", null)
                .get(GetOpenApiParamType.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob pathParameters, Glob queryParameters) throws Exception {
                        String scope = queryParameters == null ? "" : queryParameters.get(GetOpenApiParamType.scope);
                        if (Strings.isNullOrEmpty(scope)) {
                            return CompletableFuture.completedFuture(openApiDoc.getOpenApiDoc());
                        }
                        return CompletableFuture.completedFuture(openApiDoc.createOpenApiDocByTags(scope));
                    }
                }); //.declareReturnType(OpenApiType.TYPE);
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


    public class Verb {
        public final String url;
        public final GlobType pathParameters;
        public final Map<String, String> headers = new LinkedHashMap<>();
        // TODO: these are scoped
        public List<HttpOperation> operations = new ArrayList<>();


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

        public HttpReceiver complete() {
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
