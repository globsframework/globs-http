package org.globsframework.http.server.apache;

import org.globsframework.http.GlobHttpRequestHandlerFactory;

import java.util.Arrays;
import java.util.Collection;

public class RequestDispatcher {
    private final String serverInfo;
    private StrNode[] nodes = new StrNode[0];

    public RequestDispatcher(String serverInfo) {
        this.serverInfo = serverInfo;
    }


//    public void handle(HttpRequest requestObject, AsyncServerRequestHandler.ResponseTrigger responseTrigger, HttpContext context) throws HttpException, IOException {
//        int i = uri.indexOf("?");
//        String urlStr = uri.substring(1, i == -1 ? uri.length() : i); // remove first /
//        String paramStr = i == -1 ? null : uri.substring(i + 1);
//        String[] split = urlStr.split("/");
//        int min = Math.min(split.length, nodes.length - 1);
//        boolean dispatch = nodes[min].dispatch(split, paramStr, httpRequest, httpExchange, context);
//        if (!dispatch) {
//            for (int pos = min; pos >= 0; pos--) {
//                if (nodes[pos].dispatchWildcard(split, paramStr, httpRequest, httpExchange, context)) {
//                    return;
//                }
//            }
//        } else {
//            return;
//        }
//        HttpResponse response = httpExchange.getResponse();
//        response.setStatusCode(HttpStatus.SC_FORBIDDEN);
//        httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
//        HttpRequestHttpAsyncServerExchangeTree.LOGGER.warn(serverInfo + " : Unexpected path : " + urlStr);
//    }

    public GlobHttpRequestHandlerFactory createHandler(String[] path, String method, String paramStr, boolean hasBody) {
        int min = Math.min(path.length, nodes.length - 1);
        GlobHttpRequestHandlerFactory requestHandler = nodes[min].createRequestHandler(path, method, paramStr, hasBody);
        if (requestHandler != null) {
            return requestHandler;
        }
        for (int pos = min; pos >= 0; pos--) {
            requestHandler = nodes[pos].findAndCreateRequestHandler(path, method, paramStr, hasBody);
            if (requestHandler != null) {
                return requestHandler;
            }
        }
        return null;
    }

    public void register(Collection<String> path, GlobHttpRequestHandlerBuilder globHttpRequestHandler) {
        int length = nodes.length;
        if (length <= path.size()) {
            nodes = Arrays.copyOf(nodes, path.size() + 1);
            for (; length < nodes.length; length++) {
                nodes[length] = new StrNode();
            }
        }
        if (globHttpRequestHandler.hasWildcardAtEnd()) {
            nodes[path.size()].registerWildcard(path, globHttpRequestHandler);
        } else {
            nodes[path.size()].register(path, globHttpRequestHandler);
        }
    }
}
