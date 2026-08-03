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
    public static final GlobField<OpenApiInfo> info;

    @Target(OpenApiComponents.class)
    public static final GlobField<OpenApiComponents> components;

    @Target(OpenApiServers.class)
    public static final GlobArrayField<OpenApiServers> servers;

    @Target(OpenApiPath.class)
    @JsonAsObject_
    public static final GlobArrayField<OpenApiPath> paths;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiType");
        openAPIVersion = typeBuilder.declareStringField("openapi");
        info = typeBuilder.declareGlobField("info", () -> OpenApiInfo.TYPE);
        components = typeBuilder.declareGlobField("components", () -> OpenApiComponents.TYPE);
        servers = typeBuilder.declareGlobArrayField("servers", () -> OpenApiServers.TYPE);
        paths = typeBuilder.declareGlobArrayField("paths", () -> OpenApiPath.TYPE, JsonAsObject.UNIQUE_GLOB);
        TYPE = typeBuilder.build();
    }
}
