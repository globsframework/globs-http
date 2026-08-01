package org.globsframework.http.server.apache;

import java.util.Collection;

public class SubStrNode {
    private final String[] path;
     final GlobHttpRequestHandlerBuilder httpRequestHandlerBuilder;

    public SubStrNode(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
        this.path = path.toArray(String[]::new);
        this.httpRequestHandlerBuilder = globHttpRequestHandler;
    }

    boolean match(String[] path) {
        String[] p = this.path;
        int i = 0, stringsLength = p.length;
        while (i < stringsLength) {
            String s = p[i];
            if (s != null) {
                if (!s.equals(path[i])) {
                    return false;
                }
            }
            i++;
        }
        return true;
    }

    // For exact (non-wildcard) routes the request must have exactly the same number of
    // segments, otherwise a longer path would prefix-match a shorter route.
    boolean matchExact(String[] path) {
        return this.path.length == path.length && match(path);
    }
}
