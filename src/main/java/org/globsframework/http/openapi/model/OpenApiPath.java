package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiPath {
    public static final GlobType TYPE;

    @JsonValueAsField_
    public static final StringField name;

    @Target(OpenApiPathDsc.class)
    public static final GlobField put;

    @Target(OpenApiPathDsc.class)
    public static final GlobField post;

    @Target(OpenApiPathDsc.class)
    public static final GlobField patch;

    @Target(OpenApiPathDsc.class)
    public static final GlobField get;

    @Target(OpenApiPathDsc.class)
    public static final GlobField delete;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiPath");
        TYPE = typeBuilder.unCompleteType();
        name = typeBuilder.declareStringField("name", JsonValueAsField.UNIQUE_GLOB);
        put = typeBuilder.declareGlobField("put", OpenApiPathDsc.TYPE);
        post = typeBuilder.declareGlobField("post", OpenApiPathDsc.TYPE);
        patch = typeBuilder.declareGlobField("patch", OpenApiPathDsc.TYPE);
        get = typeBuilder.declareGlobField("get", OpenApiPathDsc.TYPE);
        delete = typeBuilder.declareGlobField("delete", OpenApiPathDsc.TYPE);
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiPath.class).load();
    }
}
