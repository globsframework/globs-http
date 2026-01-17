package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiComponents {
    public static final GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject_
    public static final GlobArrayField schemas;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiComponents");
        schemas = typeBuilder.declareGlobArrayField("schemas", () ->OpenApiSchemaProperty.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
