package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiResponses {
    public static final GlobType TYPE;

    @JsonValueAsField_
    public static final StringField code;

    public static final StringField description;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject_
    public static final GlobArrayField content;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiResponses");
        TYPE = typeBuilder.unCompleteType();
        code = typeBuilder.declareStringField("code", JsonValueAsField.UNIQUE_GLOB);
        description = typeBuilder.declareStringField("description");
        content = typeBuilder.declareGlobArrayField("conten", OpenApiBodyMimeType.TYPE, JsonAsObject.UNIQUE_GLOB);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiResponses.class).load();
    }
}
