package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonAsObject_;

public class OpenApiRequestBody {
    public static GlobType TYPE;

    public static StringField description;

    public static BooleanField required;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject_
    public static GlobArrayField content;

    static {
        GlobTypeLoaderFactory.create(OpenApiRequestBody.class).load();
    }
}
