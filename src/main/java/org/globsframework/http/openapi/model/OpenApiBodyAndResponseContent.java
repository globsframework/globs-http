package org.globsframework.http.openapi.model;

import org.globsframework.json.annottations.JsonAsObject;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.GlobField;

public class OpenApiBodyAndResponseContent {
    public static GlobType TYPE;

    @Target(OpenApiBodyMimeType.class)
    @JsonAsObject
    public static GlobField mimeType;


    static {
        GlobTypeLoaderFactory.create(OpenApiBodyAndResponseContent.class).load();
    }
}
