package org.globsframework.shared.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.metamodel.annotations.InitUniqueKey;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.model.Key;

public class PathIndex {
    public static GlobType TYPE;

    public static IntegerField index;

    @InitUniqueKey
    public static Key KEY;

    static {
        GlobTypeLoaderFactory.create(PathIndex.class)
                .register(GlobCreateFromAnnotation.class, annotation -> PathIndex.TYPE.instantiate()
                        .set(PathIndex.index, ((PathIndex_)annotation).value()))
                .load();
    }
}
