package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.StringField;

public class GlobFile {
    public static final GlobType TYPE;

    public static final StringField file;

    public static final StringField mimeType;

    public static final BooleanField removeWhenDelivered;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("GlobFile");
        file = typeBuilder.declareStringField("file");
        mimeType = typeBuilder.declareStringField("mimeType");
        removeWhenDelivered = typeBuilder.declareBooleanField("removeWhenDelivered");
        TYPE = typeBuilder.build();
    }
}
