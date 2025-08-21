package org.globsframework.http.server.apache;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.globsframework.http.GlobHttpRequestHandler;
import org.globsframework.http.GlobHttpRequestHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class HttpRequestHttpAsyncServerExchangeTree implements AsyncServerExchangeHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestHttpAsyncServerExchangeTree.class);
    private final RequestDispatcher requestDispatcher;
    private final HttpRequest request;
    private HttpContext context;
    private GlobHttpRequestHandlerFactory globHttpRequestHandlerFactory;
    private GlobHttpRequestHandler globHttpRequestHandler;

    public HttpRequestHttpAsyncServerExchangeTree(RequestDispatcher requestDispatcher,
                                                  HttpRequest request, HttpContext context) {
        this.requestDispatcher = requestDispatcher;
        this.request = request;
        this.context = context;
    }

    public void handleRequest(HttpRequest request, EntityDetails entityDetails, ResponseChannel responseChannel, HttpContext context) throws HttpException, IOException {
        assert this.context == context;
        assert this.request == request;
        String path = request.getPath();
        int i = path.indexOf("?");
        String urlStr = path.substring(1, i == -1 ? path.length() : i); // remove first /
        String paramStr = i == -1 ? null : path.substring(i + 1);
        String[] split = urlStr.split("/");
        if (globHttpRequestHandlerFactory != null) {
            throw new RuntimeException("Bug : duplicate call to handleRequest");
        }
        globHttpRequestHandlerFactory = requestDispatcher.createHandler(split, request.getMethod(), paramStr, entityDetails != null);
        if (globHttpRequestHandlerFactory == null) {
            responseChannel.sendResponse(new BasicHttpResponse(403), null, context);
            return;
        }
        globHttpRequestHandler = globHttpRequestHandlerFactory.create(request, entityDetails, responseChannel, context);
        if (entityDetails == null || entityDetails.getContentLength() == 0) {
            globHttpRequestHandler.callHandler();
        }
    }

    public void failed(Exception cause) {
        globHttpRequestHandler.failed(cause);
    }

    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        globHttpRequestHandler.updateCapacityToReceiveData(capacityChannel);
    }

    public void consume(ByteBuffer src) throws IOException {
        globHttpRequestHandler.consumeRequest(src);
    }

    public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
        globHttpRequestHandler.streamEnd(trailers);
    }

    public int available() {
        return globHttpRequestHandler.availableInResponse();
    }

    public void produce(DataStreamChannel channel) throws IOException {
        globHttpRequestHandler.produceResponse(channel);
    }

    public void releaseResources() {
        globHttpRequestHandler.releaseResources();
    }
}
