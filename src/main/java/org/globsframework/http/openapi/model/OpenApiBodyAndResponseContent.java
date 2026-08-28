package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiBodyAndResponseContent {
    public static final GlobType TYPE;

    public static final GlobField<OpenApiBodyMimeType> mimeType;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiBodyAndResponseContent");
        mimeType = typeBuilder.declareGlobField("mimeType", () -> OpenApiBodyMimeType.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
