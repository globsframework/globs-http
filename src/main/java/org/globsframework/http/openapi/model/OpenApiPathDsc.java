package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject_;

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
    @JsonAsObject_
    public static GlobArrayField responses;

    static {
        GlobTypeLoaderFactory.create(OpenApiPathDsc.class).load();
    }
}
