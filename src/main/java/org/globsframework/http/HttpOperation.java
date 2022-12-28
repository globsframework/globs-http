package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpOperation {

    String getComment();

    HttpOp verb();

    CompletableFuture<HttpOutputData> consume(HttpInputData data, Glob url, Glob queryParameters, Glob header) throws Exception;

    GlobType getBodyType();

    GlobType getQueryParamType();

    GlobType getReturnType();

    String[] getTags();

    void headers(HeaderConsumer headerConsumer);

    boolean hasSensitiveData();

    GlobType getHeaderType();

    interface HeaderConsumer {
        void push(String name, String value);
    }
}
