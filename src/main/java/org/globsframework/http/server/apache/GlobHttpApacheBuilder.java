package org.globsframework.http.server.apache;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.http.HttpOperation;
import org.globsframework.http.HttpServerRegister;
import org.globsframework.json.GSonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;

public class GlobHttpApacheBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobHttpApacheBuilder.class);
    private final HttpServerRegister httpServerRegister;

    public GlobHttpApacheBuilder(HttpServerRegister httpServerRegister) {
        this.httpServerRegister = httpServerRegister;
    }

    private HttpAsyncServer init(BootStratServer serverBootstrap) {
        RequestDispatcher requestDispatcher = new RequestDispatcher(httpServerRegister.serverInfo);
        for (Map.Entry<String, HttpServerRegister.Verb> stringVerbEntry : httpServerRegister.verbMap.entrySet()) {
            HttpServerRegister.Verb verb = stringVerbEntry.getValue();
            GlobHttpRequestHandlerBuilder globHttpRequestHandler = new GlobHttpRequestHandlerBuilder(httpServerRegister.serverInfo, verb.complete());
            Collection<String> path = globHttpRequestHandler.createRegExp();
            requestDispatcher.register(path, globHttpRequestHandler);

            for (HttpOperation operation : stringVerbEntry.getValue().operations) {
                MutableGlob logs = HttpServerRegister.HttpAPIDesc.TYPE.instantiate()
                        .set(HttpServerRegister.HttpAPIDesc.serverName, httpServerRegister.serverInfo)
                        .set(HttpServerRegister.HttpAPIDesc.url, stringVerbEntry.getKey())
                        .set(HttpServerRegister.HttpAPIDesc.queryParam, GSonUtils.encodeGlobType(operation.getQueryParamType()))
                        .set(HttpServerRegister.HttpAPIDesc.body, GSonUtils.encodeGlobType(operation.getBodyType()))
                        .set(HttpServerRegister.HttpAPIDesc.returnType, GSonUtils.encodeGlobType(operation.getReturnType()))
                        .set(HttpServerRegister.HttpAPIDesc.comment, operation.getComment());
                LOGGER.info(httpServerRegister.serverInfo + " Api : {}", GSonUtils.encode(logs, false));
            }
        }
//        if (Strings.isNotEmpty(serverInfo)) {
//            serverBootstrap.setServerInfo(serverInfo);
//        }
        serverBootstrap.setRequestRouter((request, context) ->
                () -> new HttpRequestHttpAsyncServerExchangeTree(requestDispatcher, request, context));
        return serverBootstrap.create();
    }

    public Server startAndWaitForStartup(H2ServerBootstrap bootstrap, int wantedPort) {
        HttpAsyncServer server = init(new BootStratServer() {
            @Override
            public void setRequestRouter(HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter) {
                bootstrap.setRequestRouter(requestRouter);
            }

            @Override
            public HttpAsyncServer create() {
                return bootstrap.create();
            }
        });
        return initHttpServer(wantedPort, server);
    }

    public Server startAndWaitForStartup(AsyncServerBootstrap bootstrap, int wantedPort) {
        HttpAsyncServer server = init(new BootStratServer() {
            @Override
            public void setRequestRouter(HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter) {
                bootstrap.setRequestRouter(requestRouter);
            }

            @Override
            public HttpAsyncServer create() {
                return bootstrap.create();
            }
        });
        return initHttpServer(wantedPort, server);
    }

    private Server initHttpServer(int wantedPort, HttpAsyncServer server) {
        try {
            server.start();
            Future<ListenerEndpoint> listen = server.listen(new InetSocketAddress(wantedPort), URIScheme.HTTP);
            ListenerEndpoint listenerEndpoint = listen.get();
            InetSocketAddress address = (InetSocketAddress) listenerEndpoint.getAddress();
            int port = address.getPort();
            LOGGER.info(httpServerRegister.serverInfo);
            return new Server(server, port);
        } catch (Exception e) {
            String message = " Fail to start server" + httpServerRegister.serverInfo;
            LOGGER.error(message);
            throw new RuntimeException(message, e);
        }
    }

}
