package org.globsframework.shared.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.GlobCreateFromAnnotation;
import org.globsframework.core.metamodel.annotations.InitUniqueKey;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class PathIndex {
    public static final GlobType TYPE;

    public static final IntegerField index;

    @InitUniqueKey
    public static final Key KEY;

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("PathIndex");
        TYPE = typeBuilder.unCompleteType();
        index = typeBuilder.declareIntegerField("index");
        typeBuilder.complete();
        typeBuilder.register(GlobCreateFromAnnotation.class, annotation -> PathIndex.TYPE.instantiate()
                .set(PathIndex.index, ((PathIndex_) annotation).value()));
        KEY = KeyBuilder.newEmptyKey(TYPE);
//        GlobTypeLoaderFactory.create(PathIndex.class)
//                .register(GlobCreateFromAnnotation.class, annotation -> PathIndex.TYPE.instantiate()
//                        .set(PathIndex.index, ((PathIndex_) annotation).value()))
//                .load();
    }
}
