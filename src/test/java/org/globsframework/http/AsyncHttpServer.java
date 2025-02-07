package org.globsframework.http;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class AsyncHttpServer {
    static byte[] responseData = "Hello World".getBytes();
    static Executor executor = Runnable::run;
            //Executors.newVirtualThreadPerTaskExecutor();


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        H2ServerBootstrap h2ServerBootstrap = H2ServerBootstrap.bootstrap()
                .setH2Config(H2Config.DEFAULT)
                .setIOReactorConfig(IOReactorConfig.custom().setSoReuseAddress(true).build());
        h2ServerBootstrap.setRequestRouter((request, context) ->
                () -> new MyAsyncServerExchangeHandler(request, context));

        HttpAsyncServer httpAsyncServer = h2ServerBootstrap.create();
        httpAsyncServer.start();
        Future<ListenerEndpoint> listen = httpAsyncServer.listen(new InetSocketAddress(4000));
        ListenerEndpoint listenerEndpoint = listen.get();
        System.out.println("port " + listenerEndpoint.getAddress());
        synchronized (httpAsyncServer) {
            httpAsyncServer.wait();
        }
    }

    private static class MyAsyncServerExchangeHandler implements AsyncServerExchangeHandler {
        private final HttpRequest request;
        private ByteBuffer data;

        public MyAsyncServerExchangeHandler(HttpRequest request, HttpContext context) {
            this.request = request;
        }

        @Override
        public void handleRequest(HttpRequest request, EntityDetails entityDetails, ResponseChannel responseChannel, HttpContext context) throws HttpException, IOException {
//            executor.execute(() -> {
                data = ByteBuffer.wrap(responseData);
                try {
                    responseChannel.sendResponse(SimpleHttpResponse.create(200),
                            new BasicEntityDetails(responseData.length, ContentType.APPLICATION_JSON), context);
                } catch (HttpException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
//            });
        }

        @Override
        public void failed(Exception cause) {
        }

        @Override
        public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        }

        @Override
        public void consume(ByteBuffer src) throws IOException {
        }

        @Override
        public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
        }

        @Override
        public int available() {
            return data.remaining();
        }

        @Override
        synchronized public void produce(DataStreamChannel channel) throws IOException {
            if (data.hasRemaining()) {
                channel.write(data);
            }
            if (!data.hasRemaining()) {
                channel.endStream();
            }
            else {
                channel.requestOutput();
            }
        }

        @Override
        public void releaseResources() {
        }
    }
}
