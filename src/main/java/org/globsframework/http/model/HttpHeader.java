package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;

/**
 * One response header, for the {@code headers} of a {@link org.globsframework.http.GlobHttpContent}.
 * <p>
 * Headers declared through {@code HttpServerRegister.addHeader} are fixed at declaration time; this is
 * how a handler sets one whose value it only knows per request (a session id, an ETag, a Location).
 */
public class HttpHeader {
    public static final GlobType TYPE;

    public static final StringField name;

    public static final StringField value;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("HttpHeader");
        name = typeBuilder.declareStringField("name");
        value = typeBuilder.declareStringField("value");
        TYPE = typeBuilder.build();
    }

    public static Glob create(String name, String value) {
        return TYPE.instantiate()
                .set(HttpHeader.name, name)
                .set(HttpHeader.value, value);
    }
}
