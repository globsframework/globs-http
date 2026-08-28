package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;

public class OpenApiResponses {
    public static final GlobType TYPE;

    public static final StringField code;

    public static final StringField description;

    public static final GlobArrayField<OpenApiBodyMimeType> content;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiResponses");
        code = typeBuilder.declareStringField("code", JsonValueAsField.UNIQUE_GLOB);
        description = typeBuilder.declareStringField("description");
        content = typeBuilder.declareGlobArrayField("content", () -> OpenApiBodyMimeType.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
