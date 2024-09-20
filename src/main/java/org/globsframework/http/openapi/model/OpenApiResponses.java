package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;

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
