package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.BlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;

public class GlobHttpContent {
    public static GlobType TYPE;

    public static BlobField content;

    public static StringField mimeType;

    public static StringField charset;

    public static IntegerField statusCode;

    static {
        GlobTypeLoaderFactory.create(GlobHttpContent.class).load();
    }
}
