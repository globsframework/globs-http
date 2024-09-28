package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.json.annottations.JsonValueAsField_;

public class OpenApiBodyMimeType {
    public static GlobType TYPE;

    @JsonValueAsField_
    public static StringField mimeType;

    @Target(OpenApiSchemaProperty.class)
    public static GlobField schema;

    static {
        GlobTypeLoaderFactory.create(OpenApiBodyMimeType.class).load();
    }
}
