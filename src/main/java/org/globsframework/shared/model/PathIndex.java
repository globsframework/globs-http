package org.globsframework.shared.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.core.metamodel.annotations.InitUniqueKey;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.Key;

public class PathIndex {
    public static GlobType TYPE;

    public static IntegerField index;

    @InitUniqueKey
    public static Key KEY;

    static {
        GlobTypeLoaderFactory.create(PathIndex.class)
                .register(GlobCreateFromAnnotation.class, annotation -> PathIndex.TYPE.instantiate()
                        .set(PathIndex.index, ((PathIndex_) annotation).value()))
                .load();
    }
}
