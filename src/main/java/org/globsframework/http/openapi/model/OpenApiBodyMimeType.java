package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiBodyMimeType {
    public static final GlobType TYPE;

    @JsonValueAsField_
    public static final StringField mimeType;

    @Target(OpenApiSchemaProperty.class)
    public static final GlobField schema;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiBodyMime");
        TYPE = typeBuilder.unCompleteType();
        mimeType = typeBuilder.declareStringField("mimeType", JsonValueAsField.UNIQUE_GLOB);
        schema = typeBuilder.declareGlobField("schema", OpenApiSchemaProperty.TYPE);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiBodyMimeType.class).load();
    }
}
