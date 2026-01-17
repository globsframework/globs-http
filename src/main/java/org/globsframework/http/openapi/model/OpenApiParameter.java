package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;

public class OpenApiParameter {
    public static final GlobType TYPE;

    public static final StringField in;

    public static final StringField name;

    public static final StringField description;

    public static final BooleanField required;

    @Target(OpenApiSchemaProperty.class)
    public static final GlobField schema;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiParameter");
        in = typeBuilder.declareStringField("in");
        name = typeBuilder.declareStringField("name");
        description = typeBuilder.declareStringField("description");
        required = typeBuilder.declareBooleanField("required");
        schema = typeBuilder.declareGlobField("schema", () -> OpenApiSchemaProperty.TYPE);
        TYPE = typeBuilder.build();
    }
}
