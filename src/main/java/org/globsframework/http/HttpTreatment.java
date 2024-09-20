package org.globsframework.http;

import org.globsframework.core.metamodel.annotations.ArgName;
import org.globsframework.core.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpTreatment {

    CompletableFuture<Glob> consume(@ArgName("body") Glob body,
                                    @ArgName("pathParameters") Glob pathParameters,
                                    @ArgName("queryParameters") Glob queryParameters) throws Exception;
}
