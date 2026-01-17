package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiBodyAndResponseContent {
    public static final GlobType TYPE;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject_
    public static final GlobField mimeType;


    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiBodyAndResponseContent");
        mimeType = typeBuilder.declareGlobField("mimeType", () -> OpenApiBodyMimeType.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
