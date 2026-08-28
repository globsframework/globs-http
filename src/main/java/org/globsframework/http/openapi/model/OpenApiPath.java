package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonValueAsField;

public class OpenApiPath {
    public static final GlobType TYPE;

    public static final StringField name;

    public static final GlobField<OpenApiPathDsc> put;

    public static final GlobField<OpenApiPathDsc> post;

    public static final GlobField<OpenApiPathDsc> patch;

    public static final GlobField<OpenApiPathDsc> get;

    public static final GlobField<OpenApiPathDsc> delete;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiPath");
        name = typeBuilder.declareStringField("name", JsonValueAsField.UNIQUE_GLOB);
        put = typeBuilder.declareGlobField("put", () -> OpenApiPathDsc.TYPE);
        post = typeBuilder.declareGlobField("post", () -> OpenApiPathDsc.TYPE);
        patch = typeBuilder.declareGlobField("patch", () -> OpenApiPathDsc.TYPE);
        get = typeBuilder.declareGlobField("get", () -> OpenApiPathDsc.TYPE);
        delete = typeBuilder.declareGlobField("delete", () -> OpenApiPathDsc.TYPE);
        TYPE = typeBuilder.build();
//        GlobTypeLoaderFactory.create(OpenApiPath.class).load();
    }
}
