package org.globsframework.http.openapi.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;

public class GetOpenApiParamType {
    public static GlobType TYPE;

    public static StringField scope;

    static {
        GlobTypeLoaderFactory.create(GetOpenApiParamType.class).load();
    }
}
