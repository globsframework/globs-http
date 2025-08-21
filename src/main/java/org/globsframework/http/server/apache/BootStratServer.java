package org.globsframework.http.server.apache;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;

public interface BootStratServer {

    void setRequestRouter(final HttpRequestMapper<Supplier<AsyncServerExchangeHandler>> requestRouter);

    HttpAsyncServer create();
}
