package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringArrayField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiPathDsc {
    public static GlobType TYPE;

    public static StringArrayField tags;

    public static StringField summary;

    public static StringField description;

    public static StringField operationId;

    @Target(OpenApiParameter.class)
    public static GlobArrayField parameters;

    @Target(OpenApiRequestBody.class)
    public static GlobField requestBody;

    @Target(OpenApiResponses.class)
    @JsonAsObject
    public static GlobArrayField responses;

    static {
        GlobTypeLoaderFactory.create(OpenApiPathDsc.class).load();
    }
}
