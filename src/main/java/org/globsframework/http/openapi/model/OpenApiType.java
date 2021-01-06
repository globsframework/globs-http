package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiType {
    public static GlobType TYPE;

    @FieldNameAnnotation("openapi")
    public static StringField openAPIVersion;

    @Target(OpenApiInfo.class)
    public static GlobField info;

    @Target(OpenApiServers.class)
    public static GlobArrayField servers;

    @Target(OpenApiPath.class)
    @JsonAsObject
    public static GlobArrayField paths;

    static {
        GlobTypeLoaderFactory.create(OpenApiType.class).load();
    }
}
