package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.Target;
import org.globsframework.core.metamodel.fields.BytesField;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.http.model.HttpHeader;

public class GlobHttpContent {
    public static final GlobType TYPE;

    public static final BytesField content;

    public static final StringField mimeType;

    public static final StringField charset;

    public static final IntegerField statusCode;

    /**
     * Response headers known only at request time. Left unset, nothing is added — the headers declared
     * on the operation still apply.
     */
    @Target(HttpHeader.class)
    public static final GlobArrayField<HttpHeader> headers;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("GlobHttpContent");
        content = typeBuilder.declareBytesField("content");
        mimeType = typeBuilder.declareStringField("mimeType");
        charset = typeBuilder.declareStringField("charset");
        statusCode = typeBuilder.declareIntegerField("statusCode");
        headers = typeBuilder.declareGlobArrayField("headers", () -> HttpHeader.TYPE);
        TYPE = typeBuilder.build();
    }
}
