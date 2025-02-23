package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiSchema {
    public static final GlobType TYPE;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject_
    public static final GlobField properties;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiSchema");
        TYPE = typeBuilder.unCompleteType();
        properties = typeBuilder.declareGlobField("properties", OpenApiSchemaProperty.TYPE, JsonAsObject.UNIQUE_GLOB);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiSchema.class).load();
    }
}
