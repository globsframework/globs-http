package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;

import java.util.concurrent.CompletableFuture;

public class DefaultHttpOperation implements HttpOperation {
    HttpOp verb;
    GlobType bodyType;
    GlobType queryType;
    HttpTreatment httpTreatment;

    public DefaultHttpOperation(HttpOp verb, GlobType bodyType, GlobType queryType, HttpTreatment httpTreatment) {
        this.verb = verb;
        this.bodyType = bodyType;
        this.queryType = queryType;
        this.httpTreatment = httpTreatment;
    }

    public DefaultHttpOperation withBody(GlobType globType) {
        bodyType = globType;
        return this;
    }

    public DefaultHttpOperation withQueryType(GlobType globType) {
        queryType = globType;
        return this;
    }

    public GlobType getBodyType() {
        return bodyType;
    }

    public GlobType getQueryParamType() {
        return queryType;
    }

    public HttpOp verb() {
        return verb;
    }

    public CompletableFuture<Glob> consume(Glob data, Glob url, Glob queryParameters) {
        return httpTreatment.consume(data, url, queryParameters);
    }
}
