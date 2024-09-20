package org.globsframework.http;

import org.globsframework.core.metamodel.annotations.ArgName;
import org.globsframework.core.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpDataTreatmentWithHeader {

    CompletableFuture<HttpOutputData> consume(HttpInputData body,
                                              @ArgName("url") Glob url,
                                              @ArgName("queryParameters") Glob queryParameters,
                                              @ArgName("headers") Glob headerType) throws Exception;
}
