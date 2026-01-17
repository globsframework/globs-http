package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class GetOpenApiParamType {
    public static final GlobType TYPE;

    public static final StringField scope;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("GetOpenApiParam");
        scope = typeBuilder.declareStringField("scope");
        TYPE = typeBuilder.build();
    }
}
