package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject_;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiResponses {
    public static GlobType TYPE;

    @JsonValueAsField_
    public static StringField code;

    public static StringField description;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject_
    public static GlobArrayField content;

    static {
        GlobTypeLoaderFactory.create(OpenApiResponses.class).load();
    }
}
