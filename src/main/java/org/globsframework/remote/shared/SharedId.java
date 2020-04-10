package org.globsframework.remote.shared;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.AutoIncrementAnnotationType;
import org.globsframework.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.metamodel.annotations.InitUniqueGlob;
import org.globsframework.metamodel.annotations.InitUniqueKey;
import org.globsframework.model.Glob;
import org.globsframework.model.Key;

public class SharedId {
    public static GlobType TYPE;

    @InitUniqueKey
    public static Key KEY;

    @InitUniqueGlob
    public static Glob INSTANCE;

    static {
        GlobTypeLoaderFactory.create(SharedId.class, "SharedId")
                .register(GlobCreateFromAnnotation.class, annotation -> INSTANCE)
                .load();
    }
}
