package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiComponents {
    public static GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject
    public static GlobArrayField schemas;

    static {
        GlobTypeLoaderFactory.create(OpenApiComponents.class).load();
    }
}
