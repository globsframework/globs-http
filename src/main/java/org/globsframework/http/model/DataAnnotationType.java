package org.globsframework.http.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.metamodel.annotations.InitUniqueKey;
import org.globsframework.model.Key;

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
