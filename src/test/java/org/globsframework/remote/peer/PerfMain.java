package org.globsframework.remote.peer;

import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.utils.Files;
import org.globsframework.utils.NanoChrono;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.*;

public class PerfMain {
    static {
    }

    static final int CLIENT_REQUEST = 1;

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final PeerToPeer peer = new DirectPeerToPeer();
        if (args.length == 0) {
            final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
                public PeerToPeer.ServerRequestProcessor createServerRequest() {
                    return new PeerToPeer.ServerRequestProcessor() {
                        public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                            try {
                                Files.copy(serverRequest.getRequestStream(), response.getResponseStream());
                            } catch (IOException e) {
                                throw new RuntimeException("intput => output", e);
                            }
                            response.complete();
                        }
                    };
                }
            }, "P2P");
            System.out.println("PerfMain.main " + serverListener.getUrl());
            serverListener.join();
        } else {
            ExecutorService executorService = Executors.newFixedThreadPool(CLIENT_REQUEST);
            Future<Object>[] futures = new Future[CLIENT_REQUEST];
            Client[] clients = new Client[CLIENT_REQUEST];
            for (int i = 0; i < futures.length; i++) {
                Client task = new Client(peer, args[0]);
                clients[i] = task;
                futures[i] = executorService.submit(task);
            }
            for (Future<Object> objectFuture : futures) {
                objectFuture.get();
            }
            peer.destroy();
            executorService.shutdown();
        }
    }

    public static class Client implements Callable<Object> {
        private final PeerToPeer peer;
        private String url;

        public Client(PeerToPeer peer, String url) {
            this.peer = peer;
            this.url = url;
        }

        public Object call() throws Exception {
            NanoChrono chrono = NanoChrono.start();
            PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
            byte[] bytes = "Hello world".getBytes("UTF-8");
            PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
            OutputStream requestStream = clientRequest.getRequestStream();
            ThreadReader threadReader = new ThreadReader(clientRequest);
            threadReader.start();
            int size = 0;
            for (int i = 0; i < 10000; i++) {
                for (int k = 0; k < 10000; k++) {
                    requestStream.write(bytes);
                    size += bytes.length;

                }
                requestStream.flush();
            }
            clientRequest.requestComplete();
            threadReader.join();
            clientRequest.end();
            clientRequestFactory.release();
            double elapsedTime = chrono.getElapsedTimeInMS();
            System.out.println("ZeroMqTest$Client.call " + elapsedTime + " ms " + (size/1024/1024) + " : " + (size / 1024. / 1024. / elapsedTime * 1000.) + " Mg/s.");
            return null;
        }
    }

    static class ThreadReader extends Thread {
        private final PeerToPeer.ClientRequest clientRequest;
        String s;

        public ThreadReader(PeerToPeer.ClientRequest clientRequest) {
            this.clientRequest = clientRequest;
        }

        public void run() {
            try {
                Files.copy(clientRequest.getResponseInputStream(), new OutputStream() {
                    public void write(int b) throws IOException {
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
