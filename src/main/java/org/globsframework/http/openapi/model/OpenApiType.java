package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiType {
    public static GlobType TYPE;

    @FieldNameAnnotation("openapi")
    public static StringField openAPIVersion;

    @Target(OpenApiInfo.class)
    public static GlobField info;

    @Target(OpenApiComponents.class)
    public static GlobField components;

    @Target(OpenApiServers.class)
    public static GlobArrayField servers;

    @Target(OpenApiPath.class)
    @JsonAsObject
    public static GlobArrayField paths;

    static {
        GlobTypeLoaderFactory.create(OpenApiType.class).load();
    }
}
