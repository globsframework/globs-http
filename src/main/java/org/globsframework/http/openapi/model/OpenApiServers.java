package org.globsframework.http.openapi.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiServers {
    public static GlobType TYPE;

    public static StringField url;

    public static StringField description;

    static {
        GlobTypeLoaderFactory.create(OpenApiServers.class).load();
    }
}
