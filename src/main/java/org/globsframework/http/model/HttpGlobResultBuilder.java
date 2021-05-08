package org.globsframework.http.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeBuilder;
import org.globsframework.metamodel.fields.GlobArrayField;
import org.globsframework.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.utils.collections.Pair;

public class HttpGlobResultBuilder {

    static public Pair<GlobType, GlobArrayField> create(GlobType globType) {
        GlobTypeBuilder globTypeBuilder = DefaultGlobTypeBuilder.init("HttpArrayOf" + globType.getName());
        GlobArrayField field = globTypeBuilder.declareGlobArrayField("values", globType);
        return Pair.makePair(globTypeBuilder.get(), field);
    }
}
