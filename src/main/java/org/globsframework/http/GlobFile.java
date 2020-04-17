package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.BooleanField;
import org.globsframework.metamodel.fields.StringField;

public class GlobFile {
    public static GlobType TYPE;

    public static StringField file;

    public static BooleanField removeWhenDelivered;

    static {
        GlobTypeLoaderFactory.create(GlobFile.class).load();
    }
}
