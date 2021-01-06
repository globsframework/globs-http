package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiResponses {
    public static GlobType TYPE;

    @JsonValueAsField
    public static StringField code;

    public static StringField description;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject
    public static GlobArrayField content;

    static {
        GlobTypeLoaderFactory.create(OpenApiResponses.class).load();
    }
}
