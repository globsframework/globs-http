package org.globsframework.http;

import org.globsframework.metamodel.GlobType;

public class DefaultHttpReceiver implements HttpReceiver {
    private final String url;
    private final GlobType urlType;
    private final HttpOperation[] httpOperations;

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
        return urlType;
    }

    public HttpOperation[] getOperations() {
        return httpOperations;
    }
}
