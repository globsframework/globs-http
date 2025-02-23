package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiPathDsc {
    public static final GlobType TYPE;

    public static final StringArrayField tags;

    public static final StringField summary;

    public static final StringField description;

    public static final StringField operationId;

    @Target(OpenApiParameter.class)
    public static final GlobArrayField parameters;

    @Target(OpenApiRequestBody.class)
    public static final GlobField requestBody;

    @Target(OpenApiResponses.class)
    @JsonAsObject_
    public static final GlobArrayField responses;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiPathDsc");
        TYPE = typeBuilder.unCompleteType();
        tags = typeBuilder.declareStringArrayField("tags");
        summary = typeBuilder.declareStringField("summary");
        description = typeBuilder.declareStringField("description");
        operationId = typeBuilder.declareStringField("operationId");
        parameters = typeBuilder.declareGlobArrayField("parameters", OpenApiParameter.TYPE);
        requestBody = typeBuilder.declareGlobField("requestBody", OpenApiRequestBody.TYPE);
        responses = typeBuilder.declareGlobArrayField("responses", OpenApiResponses.TYPE, JsonAsObject.UNIQUE_GLOB);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiPathDsc.class).load();
    }
}
