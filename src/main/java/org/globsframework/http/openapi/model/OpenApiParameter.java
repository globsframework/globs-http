package org.globsframework.http.openapi.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.BooleanField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiParameter {
    public static GlobType TYPE;

    public static StringField in;

    public static StringField name;

    public static StringField description;

    public static BooleanField required;

    @Target(OpenApiSchemaProperty.class)
    public static GlobField schema;

    static {
        GlobTypeLoaderFactory.create(OpenApiParameter.class).load();
    }
}
