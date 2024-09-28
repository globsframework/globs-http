package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.FieldName_;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiType {
    public static GlobType TYPE;

    @FieldName_("openapi")
    public static StringField openAPIVersion;

    @Target(OpenApiInfo.class)
    public static GlobField info;

    @Target(OpenApiComponents.class)
    public static GlobField components;

    @Target(OpenApiServers.class)
    public static GlobArrayField servers;

    @Target(OpenApiPath.class)
    @JsonAsObject_
    public static GlobArrayField paths;

    static {
        GlobTypeLoaderFactory.create(OpenApiType.class).load();
    }
}
