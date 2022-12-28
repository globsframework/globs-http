package org.globsframework.http;

import org.globsframework.metamodel.GlobType;

public interface MutableHttpDataOperation extends HttpOperation {
    MutableHttpDataOperation withBody(GlobType globType);

    MutableHttpDataOperation withHeader(GlobType globType);

    MutableHttpDataOperation withQueryType(GlobType globType);

    void withReturnType(GlobType type);

    void withTags(String[] tags);

    void withComment(String comment);

    void withSensitiveData(boolean hasSensitiveData);

    void addHeader(String name, String value);
}
