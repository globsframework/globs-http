package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.BlobField;
import org.globsframework.metamodel.fields.StringField;

public class GlobHttpContent {
    public static GlobType TYPE;

    public static BlobField content;

    public static StringField mimeType;

    public static StringField charset;

    static {
        GlobTypeLoaderFactory.create(GlobHttpContent.class).load();
    }
}
