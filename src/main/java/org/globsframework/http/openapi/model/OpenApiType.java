package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiType {
    public static final GlobType TYPE;

    public static final StringField openAPIVersion;

    public static final GlobField<OpenApiInfo> info;

    public static final GlobField<OpenApiComponents> components;

    public static final GlobArrayField<OpenApiServers> servers;

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
