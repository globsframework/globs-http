package org.globsframework.http.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.core.utils.collections.Pair;

public class HttpGlobResultBuilder {

    static public Pair<GlobType, GlobArrayField> create(GlobType globType) {
        GlobTypeBuilder globTypeBuilder = DefaultGlobTypeBuilder.init("HttpArrayOf" + globType.getName());
        GlobArrayField field = globTypeBuilder.declareGlobArrayField("values", () -> globType);
        return Pair.makePair(globTypeBuilder.build(), field);
    }
}
