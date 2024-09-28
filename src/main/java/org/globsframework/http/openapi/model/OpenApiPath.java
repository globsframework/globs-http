package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiPath {
    public static GlobType TYPE;

    @JsonValueAsField_
    public static StringField name;

    @Target(OpenApiPathDsc.class)
    public static GlobField put;

    @Target(OpenApiPathDsc.class)
    public static GlobField post;

    @Target(OpenApiPathDsc.class)
    public static GlobField patch;

    @Target(OpenApiPathDsc.class)
    public static GlobField get;

    @Target(OpenApiPathDsc.class)
    public static GlobField delete;

    static {
        GlobTypeLoaderFactory.create(OpenApiPath.class).load();
    }
}
