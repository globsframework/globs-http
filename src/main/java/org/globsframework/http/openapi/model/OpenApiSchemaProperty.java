package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Comment_;
import org.globsframework.core.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;

public class OpenApiSchemaProperty {
    public static GlobType TYPE;

    @JsonValueAsField
    public static StringField name;

    @Comment_("string, number, integer, boolean, array, object")
    public static StringField type;

    @Target(OpenApiSchemaProperty.class)
    public static GlobArrayField anyOf;

    @Comment_("For String: date (2017-07-21), date-time (2017-07-21T17:32:28Z), password, byte (base-64), binary")
    public static StringField format;

    public static IntegerField minimum;

    public static IntegerField maximum;

    @Target(OpenApiSchemaProperty.class)
    @JsonAsObject
    public static GlobArrayField properties;

    @Target(OpenApiSchemaProperty.class)
    public static GlobField items;

    @FieldNameAnnotation("$ref")
    public static StringField ref;

    static {
        GlobTypeLoaderFactory.create(OpenApiSchemaProperty.class).load();
    }
}
