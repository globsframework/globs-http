package org.globsframework.http.server.apache;

import org.globsframework.http.GlobHttpRequestHandlerFactory;

import java.util.Arrays;
import java.util.Collection;

public class StrNode {
    private SubStrNode[] subStrNodes = new SubStrNode[0];
    private SubStrNode[] subWithWildCard = new SubStrNode[0];

    public GlobHttpRequestHandlerFactory createRequestHandler(String[] path, String method, String paramStr, boolean hasBody) {
        for (SubStrNode subStrNode : this.subStrNodes) {
            if (subStrNode.match(path)) {
                return subStrNode.httpRequestHandlerBuilder.create(path, method, paramStr, hasBody);
            }
        }
        return null;
    }

    public GlobHttpRequestHandlerFactory findAndCreateRequestHandler(String[] path, String method, String paramStr, boolean hasBody) {
        for (SubStrNode subStrNode : this.subWithWildCard) {
            if (subStrNode.match(path)) {
                return subStrNode.httpRequestHandlerBuilder.create(path, method, paramStr, hasBody);
            }
        }
        return null;
    }

    public void register(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
        subStrNodes = Arrays.copyOf(subStrNodes, subStrNodes.length + 1);
        subStrNodes[subStrNodes.length - 1] = new SubStrNode(path, globHttpRequestHandler);
    }

    public void registerWildcard(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
        subWithWildCard = Arrays.copyOf(subWithWildCard, subWithWildCard.length + 1);
        subWithWildCard[subWithWildCard.length - 1] = new SubStrNode(path, globHttpRequestHandler);
    }
}
