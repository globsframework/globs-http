package org.globsframework.http;

import org.globsframework.model.Glob;

import java.util.concurrent.CompletableFuture;

public interface HttpTreatment {
    CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters);
}
