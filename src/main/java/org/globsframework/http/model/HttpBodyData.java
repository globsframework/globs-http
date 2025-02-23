package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.core.metamodel.annotations.InitUniqueGlob;
import org.globsframework.core.metamodel.annotations.InitUniqueKey;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class HttpBodyData {
    public static final GlobType TYPE;

    @InitUniqueKey
    public static final Key UNIQUE_KEY;

    @InitUniqueGlob
    public static final Glob UNIQUE_INSTANCE;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("HttpBodyData");
        TYPE = typeBuilder.get();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
        UNIQUE_INSTANCE = TYPE.instantiate();
        typeBuilder.register(GlobCreateFromAnnotation.class, annotation -> UNIQUE_INSTANCE);
//        GlobTypeLoaderFactory.create(HttpBodyData.class)
//                .register(GlobCreateFromAnnotation.class, annotation -> TYPE.instantiate())
//                .load();
    }

}
