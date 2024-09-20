package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class GetOpenApiParamType {
    public static GlobType TYPE;

    public static StringField scope;

    static {
        GlobTypeLoaderFactory.create(GetOpenApiParamType.class).load();
    }
}
