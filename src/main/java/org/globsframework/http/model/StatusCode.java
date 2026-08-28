package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class StatusCode {
    public static final GlobType TYPE;

    public static final Key UNIQUE_KEY;

    public static final Glob UNIQUE_INSTANCE;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("StatusCode");
        TYPE = typeBuilder.build();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
        UNIQUE_INSTANCE = TYPE.instantiate();
    }
}
