package org.globsframework.http.openapi.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.json.annottations.JsonAsObject;

public class OpenApiBodyAndResponseContent {
    public static GlobType TYPE;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject
    public static GlobField mimeType;


    static {
        GlobTypeLoaderFactory.create(OpenApiBodyAndResponseContent.class).load();
    }
}
