package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class OpenApiInfo {
    public static final GlobType TYPE;

    public static final StringField title;

    public static final StringField description;

    public static final StringField version;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiInfo");
        TYPE = typeBuilder.unCompleteType();
        title = typeBuilder.declareStringField("title");
        description = typeBuilder.declareStringField("description");
        version = typeBuilder.declareStringField("version");
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiInfo.class).load();
    }
}
