package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Comment;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.Ref;
import org.globsframework.http.HttpOperation;
import org.globsframework.http.HttpServerRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class GlobOpenApi {
    private static final String DOUBLE_STR = "double";
    private static final String NUMBER_STR = "number";
    private static final String ARRAY_STR = "array";
    private static final String BIG_DECIMAL_STR = "big-decimal";
    private static final String STRING_STR = "string";
    private static final Logger log = LoggerFactory.getLogger(GlobOpenApi.class);
    private Glob openApiDoc;
    private final HttpServerRegister httpServerRegister;

    public GlobOpenApi(HttpServerRegister httpServerRegister) {
        this.httpServerRegister = httpServerRegister;
    }

    public Glob getOpenApiDoc() {
        if (openApiDoc == null) { // if initOpenApiDoc was not called yet
            initOpenApiDoc(-1);
        }
        return openApiDoc;
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


        return openApiDoc.duplicate().set(OpenApiType.paths, paths.toArray(Glob[]::new));
    }

    private boolean hasSelectedTag(Glob path, GlobField field, String targetScope) {
        Glob pathDescription = path.get(field);
        if (pathDescription == null) {
            return false;
        }

        String[] currentScopes = pathDescription.getOrEmpty(OpenApiPathDsc.tags);
        return Arrays.asList(currentScopes).contains(targetScope);
    }

    public void initOpenApiDoc(int port) {
        Map<GlobType, Glob> schemas = new LinkedHashMap<>();
        List<Glob> paths = new ArrayList<>();
        for (Map.Entry<String, HttpServerRegister.Verb> stringVerbEntry : httpServerRegister.verbMap.entrySet()) {
            createVerbDoc(schemas, paths, stringVerbEntry);
        }
        if (openApiDoc !=  null) {
            log.warn("replace previous openApi");
        }

        openApiDoc = OpenApiType.TYPE.instantiate()
                .set(OpenApiType.openAPIVersion, "3.0.1")
                .set(OpenApiType.info, OpenApiInfo.TYPE.instantiate()
                        .set(OpenApiInfo.description, httpServerRegister.serverInfo)
                        .set(OpenApiInfo.title, httpServerRegister.serverInfo)
                        .set(OpenApiInfo.version, "1.0")
                )
                .set(OpenApiType.components, OpenApiComponents.TYPE.instantiate()
                        .set(OpenApiComponents.schemas, schemas.values().toArray(Glob[]::new)))
                .set(OpenApiType.servers, new Glob[]{OpenApiServers.TYPE.instantiate()
                        .set(OpenApiServers.url, "http://localhost:" + port)})
                .set(OpenApiType.paths, paths.toArray(Glob[]::new));
    }

    private void createVerbDoc(Map<GlobType, Glob> schemas, List<Glob> paths, Map.Entry<String, HttpServerRegister.Verb> stringVerbEntry) {
        HttpServerRegister.Verb verb = stringVerbEntry.getValue();
        MutableGlob path = OpenApiPath.TYPE.instantiate();
        paths.add(path);
        path.set(OpenApiPath.name, verb.url);
        for (HttpOperation operation : stringVerbEntry.getValue().operations) {
            createOperationDoc(schemas, verb, path, operation);

        }
    }

    private void createOperationDoc(Map<GlobType, Glob> schemas, HttpServerRegister.Verb verb, MutableGlob path, HttpOperation operation) {
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

    private List<Glob> getOperationPathParameters(Map<GlobType, Glob> schemas, HttpServerRegister.Verb verb) {
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
        desc.set(OpenApiPathDsc.parameters, parameters.toArray(Glob[]::new));
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
            schema.set(OpenApiSchemaProperty.properties, param.toArray(Glob[]::new));
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
                schema.set(OpenApiSchemaProperty.anyOf, sub.toArray(Glob[]::new));
//                ref.set(OpenApiSchemaProperty.name, field.getName());
//                        .set(OpenApiSchemaProperty.format, "binary")
//                        .set(OpenApiSchemaProperty.type, "object");
                p.set(schema);
            }

            private List<Glob> extractUnion(Collection<GlobType> targetTypes) {
                List<Glob> sub = new ArrayList<>();
                for (GlobType targetType : targetTypes) {
                    String name = targetType.getName() + "_union";
                    var first = schemas.entrySet().stream().filter(e -> e.getKey().getName().equals(name)).findFirst();
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
                                .set(OpenApiSchemaProperty.anyOf, sub.toArray(Glob[]::new))
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
            public void visitBytes(BytesField field) throws Exception {
                MutableGlob ref = OpenApiSchemaProperty.TYPE.instantiate()
                        .set(OpenApiSchemaProperty.name, field.getName())
                        .set(OpenApiSchemaProperty.format, "binary")
                        .set(OpenApiSchemaProperty.type, "object");
                p.set(ref);
            }
        });
        return p.get();
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

}
