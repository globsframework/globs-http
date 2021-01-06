package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiPath {
    public static GlobType TYPE;

    @JsonValueAsField
    public static StringField name;

    @Target(OpenApiPathDsc.class)
    public static GlobField put;

    @Target(OpenApiPathDsc.class)
    public static GlobField post;

    @Target(OpenApiPathDsc.class)
    public static GlobField get;

    @Target(OpenApiPathDsc.class)
    public static GlobField delete;

    static {
        GlobTypeLoaderFactory.create(OpenApiPath.class).load();
    }
}
