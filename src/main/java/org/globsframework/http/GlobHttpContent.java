package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;

public class GlobHttpContent {
    public static GlobType TYPE;

    public static StringField content;

    public static StringField mimeType;

    static {
        GlobTypeLoaderFactory.create(GlobHttpContent.class).load();
    }
}
