package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Comment_;
import org.globsframework.core.metamodel.annotations.FieldName_;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonAsObject_;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiSchemaProperty {
    public static final GlobType TYPE;

    @JsonValueAsField_
    public static final StringField name;

    @Comment_("string, number, integer, boolean, array, object")
    public static final StringField type;

    @Target(OpenApiSchemaProperty.class)
    public static final GlobArrayField anyOf;

    @Comment_("For String: date (2017-07-21), date-time (2017-07-21T17:32:28Z), password, byte (base-64), binary")
    public static final StringField format;

    public static final IntegerField minimum;

    public static final IntegerField maximum;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject_
    public static final GlobArrayField properties;

    @Target(OpenApiSchemaProperty.class)
    public static final GlobField items;

    @FieldName_("$ref")
    public static final StringField ref;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("OpenApiSchemaProperty");
        TYPE = typeBuilder.unCompleteType();
        name = typeBuilder.declareStringField("name", JsonValueAsField.UNIQUE_GLOB);
        type = typeBuilder.declareStringField("type");
        anyOf = typeBuilder.declareGlobArrayField("anyOf", OpenApiSchemaProperty.TYPE);
        format = typeBuilder.declareStringField("format");
        minimum = typeBuilder.declareIntegerField("minimum");
        maximum = typeBuilder.declareIntegerField("maximum");
        properties = typeBuilder.declareGlobArrayField("properties", OpenApiSchemaProperty.TYPE, JsonAsObject.UNIQUE_GLOB);
        items = typeBuilder.declareGlobField("items", OpenApiSchemaProperty.TYPE);
        ref = typeBuilder.declareStringField("$ref");
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(OpenApiSchemaProperty.class).load();
    }
}
