package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.StringField;

public class OpenApiServers {
    public static GlobType TYPE;

    public static StringField url;

    public static StringField description;

    static {
        GlobTypeLoaderFactory.create(OpenApiServers.class).load();
    }
}
