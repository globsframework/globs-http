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
        TYPE = typeBuilder.unCompleteType();
        scope = typeBuilder.declareStringField("scope");
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(GetOpenApiParamType.class).load();
    }
}
