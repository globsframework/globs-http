package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.BlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;

public class GlobHttpContent {
    public static final GlobType TYPE;

    public static final BlobField content;

    public static final StringField mimeType;

    public static final StringField charset;

    public static final IntegerField statusCode;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("GlobHttpContent");
        TYPE = typeBuilder.unCompleteType();
        content = typeBuilder.declareBlobField("content");
        mimeType = typeBuilder.declareStringField("mimeType");
        charset = typeBuilder.declareStringField("charset");
        statusCode = typeBuilder.declareIntegerField("statusCode");
        typeBuilder.complete();
//        GlobTypeLoaderFactory.create(GlobHttpContent.class).load();
    }
}
