package org.globsframework.http.server.apache;

import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;

public class Server {
    private final HttpAsyncServer server;
    private final int port;

    public Server(HttpAsyncServer server, int port) {
        this.server = server;
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public HttpAsyncServer getServer() {
        return server;
    }
}
