package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;

import java.util.HashMap;
import java.util.Map;

public class DefaultHttpReceiver implements HttpReceiver {
    private final String url;
    private final GlobType urlType;
    private final HttpOperation[] httpOperations;
    private final Map<String, String> headers = new HashMap<>();

    public DefaultHttpReceiver(String url, GlobType urlType, HttpOperation... httpOperations) {
        this.url = url;
        this.urlType = urlType;
        this.httpOperations = httpOperations;
    }

    public DefaultHttpReceiver(HttpOperation[] httpOperations) {
        this.httpOperations = httpOperations;
        url = null;
        urlType = null;
    }

    public String getUrl() {
        return url;
    }

    public GlobType getUrlType() {
        return urlType != null ? urlType : DefaultHttpOperation.EMPTY;
    }

    public HttpOperation[] getOperations() {
        return httpOperations;
    }

    public void headers(HttpOperation.HeaderConsumer headerConsumer) {
        headers.forEach(headerConsumer::push);
    }

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

}
