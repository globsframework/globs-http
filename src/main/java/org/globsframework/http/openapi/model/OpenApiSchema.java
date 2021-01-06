package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobField;

public class OpenApiSchema {
    public static GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject
    public static GlobField properties;

    static {
        GlobTypeLoaderFactory.create(OpenApiSchema.class).load();
    }
}
