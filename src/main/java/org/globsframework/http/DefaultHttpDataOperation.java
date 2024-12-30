package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.core.model.Glob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultHttpDataOperation implements MutableHttpDataOperation {
    public static final GlobType EMPTY = DefaultGlobTypeBuilder.init("Empty").get();
    private final HttpOp verb;
    private GlobType bodyType;
    private GlobType queryType;
    private Glob emptyQuery;
    private GlobType returnType;
    private String[] tags;
    private final HttpDataTreatmentWithHeader httpTreatment;
    private String comment;
    private final Map<String, String> headers = new HashMap<>();
    private boolean hasSensitiveData = false;
    private GlobType headerType;
    private Glob emptyHeader;

    public DefaultHttpDataOperation(HttpOp verb, GlobType bodyType, GlobType queryType, HttpDataTreatmentWithHeader httpTreatment) {
        this.verb = verb;
        this.bodyType = bodyType;
        this.queryType = queryType;
        this.httpTreatment = httpTreatment;
        emptyQuery = queryType != null ? queryType.instantiate() : null;
        emptyHeader = headerType != null ? headerType.instantiate() : null;
    }

    public MutableHttpDataOperation withBody(GlobType globType) {
        bodyType = globType;
        return this;
    }

    public MutableHttpDataOperation withHeader(GlobType globType) {
        headerType = globType;
        return this;
    }

    public MutableHttpDataOperation withQueryType(GlobType globType) {
        queryType = globType;
        return this;
    }

    public void withReturnType(GlobType type) {
        this.returnType = type;
    }

    public void withTags(String[] tags) {
        this.tags = tags;
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

    public String[] getTags() {
        return tags;
    }

    public void headers(HeaderConsumer headerConsumer) {
        headers.forEach(headerConsumer::push);
    }

    public boolean hasSensitiveData() {
        return hasSensitiveData;
    }

    public GlobType getHeaderType() {
        return headerType;
    }

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public String getComment() {
        return comment;
    }

    public HttpOp verb() {
        return verb;
    }

    public CompletableFuture<HttpOutputData> consume(HttpInputData data, Glob url, Glob queryParameters, Glob header) throws Exception {
        return httpTreatment.consume(data,
                url, queryParameters == null ? emptyQuery : queryParameters,
                header == null ? emptyHeader : header);
    }

    public void withComment(String comment) {
        this.comment = comment;
    }

    public void withSensitiveData(boolean hasSensitiveData) {
        this.hasSensitiveData = hasSensitiveData;
    }
}
