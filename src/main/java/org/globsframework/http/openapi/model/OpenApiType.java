package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.FieldName_;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiType {
    public static final GlobType TYPE;

    @FieldName_("openapi")
    public static final StringField openAPIVersion;

    @Target(OpenApiInfo.class)
    public static final GlobField info;

    @Target(OpenApiComponents.class)
    public static final GlobField components;

    @Target(OpenApiServers.class)
    public static final GlobArrayField servers;

    @Target(OpenApiPath.class)
    @JsonAsObject_
    public static final GlobArrayField paths;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiType");
        TYPE = typeBuilder.unCompleteType();
        openAPIVersion = typeBuilder.declareStringField("openapi");
        info = typeBuilder.declareGlobField("info", OpenApiInfo.TYPE);
        components = typeBuilder.declareGlobField("components", OpenApiComponents.TYPE);
        servers = typeBuilder.declareGlobArrayField("servers", OpenApiServers.TYPE);
        paths = typeBuilder.declareGlobArrayField("paths", OpenApiPath.TYPE, JsonAsObject.UNIQUE_GLOB);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiType.class).load();
    }
}
