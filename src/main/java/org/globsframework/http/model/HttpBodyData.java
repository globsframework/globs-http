package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class HttpBodyData {
    public static final GlobType TYPE;

    public static final Key UNIQUE_KEY;

    public static final Glob UNIQUE_INSTANCE;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("HttpBodyData");
        TYPE = typeBuilder.build();
        UNIQUE_INSTANCE = TYPE.instantiate();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
    }
}
