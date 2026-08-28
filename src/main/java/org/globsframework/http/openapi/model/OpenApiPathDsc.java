package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiPathDsc {
    public static final GlobType TYPE;

    public static final StringArrayField tags;

    public static final StringField summary;

    public static final StringField description;

    public static final StringField operationId;

    public static final GlobArrayField<OpenApiParameter> parameters;

    public static final GlobField<OpenApiRequestBody> requestBody;

    public static final GlobArrayField<OpenApiResponses> responses;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiPathDsc");
        tags = typeBuilder.declareStringArrayField("tags");
        summary = typeBuilder.declareStringField("summary");
        description = typeBuilder.declareStringField("description");
        operationId = typeBuilder.declareStringField("operationId");
        parameters = typeBuilder.declareGlobArrayField("parameters", () -> OpenApiParameter.TYPE);
        requestBody = typeBuilder.declareGlobField("requestBody", () -> OpenApiRequestBody.TYPE);
        responses = typeBuilder.declareGlobArrayField("responses", () -> OpenApiResponses.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
