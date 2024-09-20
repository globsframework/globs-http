package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.core.metamodel.annotations.InitUniqueKey;
import org.globsframework.core.model.Key;

public class DataAnnotationType {
    public static GlobType TYPE;

    @InitUniqueKey
    public static Key UNIQUE_KEY;

    static {
        GlobTypeLoaderFactory.create(DataAnnotationType.class)
                .register(GlobCreateFromAnnotation.class, annotation -> TYPE.instantiate())
                .load();
    }

}
