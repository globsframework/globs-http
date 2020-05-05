package org.globsframework.http;

import org.globsframework.metamodel.annotations.ArgName;
import org.globsframework.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpTreatment {
    CompletableFuture<Glob> consume(@ArgName("Body") Glob body,
                                    @ArgName("url")Glob url,
                                    @ArgName("queryParameters")Glob queryParameters) throws Exception;
}
