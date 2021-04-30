package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobArrayField;

public class OpenApiComponents {
    public static GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject
    public static GlobArrayField schemas;

    static {
        GlobTypeLoaderFactory.create(OpenApiComponents.class).load();
    }
}
