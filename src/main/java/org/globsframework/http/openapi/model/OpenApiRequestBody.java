package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.BooleanField;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiRequestBody {
    public static GlobType TYPE;

    public static StringField description;

    public static BooleanField required;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject
    public static GlobArrayField content;

    static {
        GlobTypeLoaderFactory.create(OpenApiRequestBody.class).load();
    }
}
