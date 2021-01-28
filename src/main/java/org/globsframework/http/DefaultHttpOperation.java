package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.model.Glob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultHttpOperation implements HttpOperation {
    public static final GlobType EMPTY = DefaultGlobTypeBuilder.init("Empty").get();
    private final HttpOp verb;
    GlobType bodyType;
    GlobType queryType;
    GlobType returnType;
    HttpTreatment httpTreatment;
    private String comment;
    private final Map<String, String> headers = new HashMap<>();


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

    void withReturnType(GlobType type){
        this.returnType = type;
    }

    public GlobType getBodyType() {
        return bodyType;
    }

    public GlobType getQueryParamType() {
        return queryType != null ? queryType : EMPTY;
    }

    public GlobType getReturnType() {
        return returnType;
    }

    public void headers(HeaderConsumer headerConsumer) {
        headers.forEach(headerConsumer::push);
    }

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public String getComment(){
        return comment;
    }

    public HttpOp verb() {
        return verb;
    }

    public CompletableFuture<Glob> consume(Glob data, Glob url, Glob queryParameters) throws Exception {
        return httpTreatment.consume(data, url, queryParameters);
    }

    public void withComment(String comment) {
        this.comment = comment;
    }
}
