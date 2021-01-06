package org.globsframework.http.openapi.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiInfo {
    public static GlobType TYPE;

    public static StringField title;

    public static StringField description;

    public static StringField version;

    static {
        GlobTypeLoaderFactory.create(OpenApiInfo.class).load();
    }
}
