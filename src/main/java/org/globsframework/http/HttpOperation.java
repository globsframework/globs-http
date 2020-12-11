package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpOperation {

    HttpOp verb();

    CompletableFuture<Glob> consume(Glob data, Glob url, Glob queryParameters) throws Exception;

    GlobType getBodyType();

    GlobType getQueryParamType();

    GlobType getReturnType();
}
