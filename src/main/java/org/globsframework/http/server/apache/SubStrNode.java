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
        String[] strings = this.path;
        for (int i = 0, stringsLength = strings.length; i < stringsLength; i++) {
            String s = strings[i];
            if (s != null) {
                if (!s.equals(path[i])) {
                    return false;
                }
            }
        }
        return true;
    }
}
