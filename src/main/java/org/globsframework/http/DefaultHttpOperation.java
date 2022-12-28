package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.model.Glob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultHttpOperation implements MutableHttpDataOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpOperation.class);
    public static final GlobType EMPTY = DefaultGlobTypeBuilder.init("Empty").get();
    private final HttpOp verb;
    private GlobType bodyType;
    private Glob emptyBody;
    private GlobType queryType;
    private Glob emptyQuery;
    private GlobType returnType;
    private String[] tags;
    private final HttpTreatmentWithHeader httpTreatment;
    private String comment;
    private final Map<String, String> headers = new HashMap<>();
    private boolean hasSensitiveData = false;
    private GlobType headerType;
    private Glob emptyHeader;

    public DefaultHttpOperation(HttpOp verb, GlobType bodyType, GlobType queryType, HttpTreatmentWithHeader httpTreatment) {
        this.verb = verb;
        this.bodyType = bodyType;
        this.queryType = queryType;
        this.httpTreatment = httpTreatment;
        emptyBody = bodyType != null ? bodyType.instantiate() : null;
        emptyQuery = queryType != null ? queryType.instantiate() : null;
        emptyHeader = headerType != null ? headerType.instantiate() : null;
    }

    public DefaultHttpOperation withBody(GlobType globType) {
        bodyType = globType;
        return this;
    }

    public DefaultHttpOperation withHeader(GlobType globType) {
        headerType = globType;
        return this;
    }

    public DefaultHttpOperation withQueryType(GlobType globType) {
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
        if (data == null || !data.isGlob() || data.asGlob() == null) {
            final CompletableFuture<Glob> consume = httpTreatment.consume(emptyBody, url, queryParameters == null ? emptyQuery : queryParameters,
                    header == null ? emptyHeader : header);
            return consume == null ? CompletableFuture.completedFuture(null) : consume.thenApply(HttpOutputData::asGlob);
        } else {
            final CompletableFuture<Glob> consume = httpTreatment.consume(data.asGlob(), url, queryParameters == null ? emptyQuery : queryParameters,
                    header == null ? emptyHeader : header);
            return consume == null ? CompletableFuture.completedFuture(null) : consume.thenApply(HttpOutputData::asGlob);
        }
    }

    public void withComment(String comment) {
        this.comment = comment;
    }

    public void withSensitiveData(boolean hasSensitiveData) {
        this.hasSensitiveData = hasSensitiveData;
    }
}
