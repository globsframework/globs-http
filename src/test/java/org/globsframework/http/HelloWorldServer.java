package org.globsframework.http;

import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.globsframework.utils.collections.Pair;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloWorldServer {
    private final ServerBootstrap bootstrap;
    private final HttpServerRegister httpServerRegister;
    private HttpServer httpServer;
//    private ExecutorService executorService = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            HelloWorldServer helloWorldServer = new HelloWorldServer();
            helloWorldServer.run();
            helloWorldServer.waitEnd();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("HelloWorldServer error " + e.getMessage());
            System.exit(1);
        }
    }

    public HelloWorldServer() {
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoReuseAddress(true)
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        bootstrap = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setIOReactorConfig(config);

        httpServerRegister = new HttpServerRegister("TestServer/1.1");
        httpServerRegister.register("/", null)
                .getBin(null, null, (body, url, queryParameters, header) -> {
                    CompletableFuture<HttpOutputData> future = new CompletableFuture<>();
//                    executorService.submit(() -> {
                        future.complete(HttpOutputData.asStream(
                                new ByteArrayInputStream("Hello world".getBytes(StandardCharsets.UTF_8))));
//                    });
                    return future;
                });
    }

    void run() throws IOException {
        Pair<HttpServer, Integer> httpServerIntegerPair = httpServerRegister.startAndWaitForStartup(bootstrap);
        System.out.println("listen on port " + httpServerIntegerPair.getSecond());
        httpServer = httpServerIntegerPair.getFirst();
    }
    void waitEnd() throws InterruptedException {
        httpServer.awaitTermination(-1, TimeUnit.DAYS);
    }
}
