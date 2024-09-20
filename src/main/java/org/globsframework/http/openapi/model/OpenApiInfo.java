package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class OpenApiInfo {
    public static GlobType TYPE;

    public static StringField title;

    public static StringField description;

    public static StringField version;

    static {
        GlobTypeLoaderFactory.create(OpenApiInfo.class).load();
    }
}
