package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Comment;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;

public class OpenApiSchemaProperty {
    public static final GlobType TYPE;

    public static final StringField name;

    public static final StringField type;

    public static final GlobArrayField<OpenApiSchemaProperty> anyOf;

    public static final StringField format;

    public static final IntegerField minimum;

    public static final IntegerField maximum;

    public static final GlobArrayField<OpenApiSchemaProperty> properties;

    public static final GlobField<OpenApiSchemaProperty> items;

    public static final StringField ref;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiSchemaProperty");
        name = typeBuilder.declareStringField("name", JsonValueAsField.UNIQUE_GLOB);
        type = typeBuilder.declareStringField("type", Comment.create("string, number, integer, boolean, array, object"));
        anyOf = typeBuilder.declareGlobArrayField("anyOf", () -> OpenApiSchemaProperty.TYPE);
        format = typeBuilder.declareStringField("format",
                Comment.create("For String: date (2017-07-21), date-time (2017-07-21T17:32:28Z), password, byte (base-64), binary"));
        minimum = typeBuilder.declareIntegerField("minimum");
        maximum = typeBuilder.declareIntegerField("maximum");
        properties = typeBuilder.declareGlobArrayField("properties", () -> OpenApiSchemaProperty.TYPE, JsonAsObject.UNIQUE_GLOB);
        items = typeBuilder.declareGlobField("items", () -> OpenApiSchemaProperty.TYPE);
        ref = typeBuilder.declareStringField("$ref");
        TYPE = typeBuilder.build();
    }
}
