package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiRequestBody {
    public static final GlobType TYPE;

    public static final StringField description;

    public static final BooleanField required;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject_
    public static final GlobArrayField content;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiRequestBody");
        description = typeBuilder.declareStringField("description");
        required = typeBuilder.declareBooleanField("required");
        content = typeBuilder.declareGlobArrayField("content", () -> OpenApiBodyMimeType.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
