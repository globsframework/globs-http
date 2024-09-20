package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiSchema {
    public static GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject
    public static GlobField properties;

    static {
        GlobTypeLoaderFactory.create(OpenApiSchema.class).load();
    }
}
