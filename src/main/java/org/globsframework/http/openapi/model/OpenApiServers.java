package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class OpenApiServers {
    public static final GlobType TYPE;

    public static final StringField url;

    public static final StringField description;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiServers");
        url = typeBuilder.declareStringField("url");
        description = typeBuilder.declareStringField("description");
        TYPE = typeBuilder.build();
    }
}
