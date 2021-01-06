package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.json.annottations.JsonValueAsField;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.StringField;

public class OpenApiBodyMimeType {
    public static GlobType TYPE;

    @JsonValueAsField
    public static StringField mimeType;

    @Target(OpenApiSchemaProperty.class)
    public static GlobField schema;

    static {
        GlobTypeLoaderFactory.create(OpenApiBodyMimeType.class).load();
    }
}
