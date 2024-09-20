package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.StringField;

public class GlobFile {
    public static GlobType TYPE;

    public static StringField file;

    public static StringField mimeType;

    public static BooleanField removeWhenDelivered;

    static {
        GlobTypeLoaderFactory.create(GlobFile.class).load();
    }
}
