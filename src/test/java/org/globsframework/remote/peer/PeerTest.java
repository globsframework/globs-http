package org.globsframework.remote.peer;

import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.utils.Files;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedInputOutputFactory;
import org.globsframework.utils.serialization.SerializedOutput;
import org.junit.Assert;
import org.junit.Ignore;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.Assert.assertEquals;


public class PeerTest {

    public static final int CLIENT_REQUEST = 10;

    static {
        System.setProperty("glob.peer.buffer.size", "100");
    }

    @Ignore
    public void testEndToEnd() throws Exception {
        final PeerToPeer peer = new DirectPeerToPeer();
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

        ExecutorService executorService = Executors.newFixedThreadPool(CLIENT_REQUEST);
        Future<Object>[] futures = new Future[CLIENT_REQUEST];
        Client[] clients = new Client[CLIENT_REQUEST];
        for (int i = 0; i < futures.length; i++) {
            Client task = new Client(peer, serverListener.getUrl());
            clients[i] = task;
            futures[i] = executorService.submit(task);
        }
        for (Future<Object> objectFuture : futures) {
            objectFuture.get();
        }
        serverListener.stop();
        peer.destroy();
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
            for (int i = 0; i < 1000; i++) {
                int size = 0;
                for (int k = 0; k < 1000; k++) {
                    requestStream.write(bytes);
                    size += bytes.length;
                }
                requestStream.flush();
            }
            clientRequest.requestComplete();
            threadReader.join();
            clientRequest.end();
            clientRequestFactory.release();
            System.out.println("Client.call " + chrono.getElapsedTimeInMS() + " ms");
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
                final byte[] bytes = "Hello world".getBytes("UTF-8");
                final byte[] readed = new byte[bytes.length];
                Files.copy(clientRequest.getResponseInputStream(), new OutputStream() {
                    int index = 0;

                    public void write(int b) throws IOException {
                        readed[index++] = (byte) b;
                        if (index == bytes.length) {
                            for (int i = 0; i < readed.length; i++) {
                                if (readed[i] != bytes[i]){
                                    Assert.assertArrayEquals(bytes, readed);
                                }
                            }
                            index = 0;
                        }
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Ignore
    public void testOneOp() throws Exception {

        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        InputStream requestStream = serverRequest.getRequestStream();
                        OutputStream responseStream = response.getResponseStream();
                        SerializedInput serializedInput = SerializedInputOutputFactory.init(requestStream);
                        long value = serializedInput.readNotNullLong();
                        while (true) {
                            long next = serializedInput.readNotNullLong();
                            if (next == -1) {
                                break;
                            }
                            if (next != value + 1) {
                                throw new RuntimeException("Bug " + next + " != " + (value + 1));
                            }
                            if ((next % 10000000) == 0) {
                                System.out.println("receive " + next);
                            }
                            value = next;
                        }
                        SerializedOutput serializedOutput = SerializedInputOutputFactory.init(responseStream);
                        for (long i = 0; i < value; i++) {
                            serializedOutput.write(i);
                        }
                        serializedOutput.write(-1l);
                        response.complete();
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
        PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
        SerializedOutput serializedOutput = SerializedInputOutputFactory.init(clientRequest.getRequestStream());
        for (long i = 0; i < 10000000 * 2; i++) {
            serializedOutput.write(i);
        }
        serializedOutput.write(-1l);
        clientRequest.requestComplete();
        InputStream responseInputStream = clientRequest.getResponseInputStream();
        SerializedInput serializedInput = SerializedInputOutputFactory.init(responseInputStream);
        long value = serializedInput.readNotNullLong();
        while (true) {
            long next = serializedInput.readNotNullLong();
            if (next == -1) {
                break;
            }
            if (next != value + 1) {
                throw new RuntimeException("Bug " + next + " != " + (value + 1));
            }
            value = next;
        }
    }


    @Ignore
    public void testInc() throws Exception {

        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
//                            Files.copy(serverRequest.getRequestStream(), response.getResponseStream());
                            byte[] b = new byte[10];
                            Assert.assertEquals(serverRequest.getRequestStream().read(b), 5);
                            Assert.assertEquals(new String(b, 0, 5), "hello");
                            response.getResponseStream().write("world".getBytes());
                            response.complete();
                        } catch (IOException e) {
                            throw new RuntimeException("intput => output", e);
                        }
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
        PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
        clientRequest.getRequestStream().write("hello".getBytes());
        clientRequest.requestComplete();
        byte[] b = new byte[10];
        Assert.assertEquals(clientRequest.getResponseInputStream().read(b), 5);
        Assert.assertEquals(new String(b, 0, 5), "world");
    }


    @Ignore
    public void testWithTimeout() throws Exception {

        System.setProperty("glob.peer.direct.ping", "100");
        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
//                            Files.copy(serverRequest.getRequestStream(), response.getResponseStream());
                            byte[] b = new byte[10];
                            int read = serverRequest.getRequestStream().read(b);
                            Assert.assertEquals(5, read);
                            Assert.assertEquals("hello", new String(b, 0, 5));
                            response.getResponseStream().write("world".getBytes());
                            response.complete();
                        } catch (IOException e) {
                            throw new RuntimeException("intput => output", e);
                        }
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        PeerToPeer.ClientRequestFactory clientRequestFactory =
                peer.getClientRequestFactory(url);
        PeerToPeer.ClientRequest c1 = doCall(clientRequestFactory);
        PeerToPeer.ClientRequest c2 = doCall(clientRequestFactory);
        PeerToPeer.ClientRequest c3 = doCall(clientRequestFactory);
        PeerToPeer.ClientRequest c4 = doCall(clientRequestFactory);
        c1.end();
        c2.end();
        c3.end();
        c4.end();
        c1 = doCall(clientRequestFactory);
        c2 = doCall(clientRequestFactory);
        Thread.sleep(500);
        c3 = doCall(clientRequestFactory);
        c4 = doCall(clientRequestFactory);
        c1.end();
        c2.end();
        c3.end();
        c4.end();
        c1 = doCall(clientRequestFactory);
        c1.end();
    }

    private PeerToPeer.ClientRequest doCall(PeerToPeer.ClientRequestFactory clientRequestFactory) throws IOException {
        PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
        clientRequest.getRequestStream().write("hello".getBytes());
        clientRequest.requestComplete();
        byte[] b = new byte[10];
        Assert.assertEquals(5, clientRequest.getResponseInputStream().read(b));
        Assert.assertEquals("world", new String(b, 0, 5));
        return clientRequest;
    }

    @Ignore
    public void testWithThrow() throws Exception {
        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
                            OutputStream responseStream = response.getResponseStream();
                            responseStream.write("world".getBytes());
                            throw new RuntimeException("Bug");
                        } catch (IOException e) {
                            throw new RuntimeException("intput => output", e);
                        }
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
        PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
        clientRequest.getRequestStream().write("hello".getBytes());
        clientRequest.requestComplete();
        InputStream responseInputStream = clientRequest.getResponseInputStream();
        byte[] b = new byte[255];
        int len = responseInputStream.read(b);
        assertEquals(len, 5);
        clientRequest.end();
        clientRequest = clientRequestFactory.create();
        clientRequest.getRequestStream().write("hello2".getBytes());
        responseInputStream = clientRequest.getResponseInputStream();
        clientRequest.requestComplete();
        len = responseInputStream.read(b);
        clientRequest.end();
        assertEquals(len, 5);
    }

    @Ignore
    public void testWithThrowAtStartAndReuse() throws Exception {
        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
                            InputStream requestStream = serverRequest.getRequestStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(requestStream));
                            String s = reader.readLine();
//                            while (requestStream.read() != -1);
                            OutputStream responseStream = response.getResponseStream();
                            for (int i = 0; i < Integer.parseInt(s); i++){
                                responseStream.write('a');
                            }
//                            throw new RuntimeException("Throw an exception");
                        } catch (IOException e) {
                           throw new RuntimeException(e);
                        }
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
        for (int i = 0; i< 1000; i++) {
            PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
//            if (i == 0){
//                Thread.sleep(20 * 1000);
//            }
            OutputStream requestStream = clientRequest.getRequestStream();
            requestStream.write((Integer.toString(i) + "\n").getBytes());
            for (int j = 0; j < i ; j++) {
                requestStream.write('b');
            }
            clientRequest.requestComplete();
            InputStream responseInputStream = clientRequest.getResponseInputStream();
            for (int j = 1; j < i ; j++) {
                assertEquals('a', responseInputStream.read());
            }
            clientRequest.end();
        }
    }

    @Ignore
    public void testWithoutEnd() throws Exception {
        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(CLIENT_REQUEST, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
                            byte[] b = new byte[255];
                            InputStream requestStream = serverRequest.getRequestStream();
                            int size = 5;
                            while (size - requestStream.read(b) != 0);
                            OutputStream responseStream = response.getResponseStream();
                            responseStream.write("world".getBytes());
                            response.complete();
                        } catch (IOException e) {
                            throw new RuntimeException("intput => output", e);
                        }
                    }
                };
            }
        }, "P2P");

        String url = serverListener.getUrl();

        for (int i = 0; i < 100; i++) {
            PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
            PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
            System.out.println("to be finalize : " + clientRequest);
            Toto toto = new Toto(clientRequest);
            clientRequest.getRequestStream().write("hello".getBytes());
            clientRequest.requestComplete();
            InputStream responseInputStream = clientRequest.getResponseInputStream();
            byte[] b = new byte[255];
            int len = responseInputStream.read(b);
            assertEquals(len, 5);
//            clientRequest.end();
            toto.call();
            clientRequest = clientRequestFactory.create();
            System.out.println("not to be finalize " + clientRequest);
            clientRequest.getRequestStream().write("hello".getBytes());
            responseInputStream = clientRequest.getResponseInputStream();
            clientRequest.requestComplete();
            len = responseInputStream.read(b);
//            clientRequest.end();
            assertEquals(len, 5);
//            if ((i % 10) == 0){
//                System.gc();
//            }
        }
    }

    static class Toto {
        PeerToPeer.ClientRequest clientRequest;

        public Toto(PeerToPeer.ClientRequest clientRequest) {
            this.clientRequest = clientRequest;
        }

        protected void finalize() throws Throwable {
            super.finalize();
            System.out.println("Toto.finalize " + clientRequest);
            clientRequest.end();
        }

        public void call() {
            System.out.println("Toto.call " + clientRequest);
        }
    }

    public void testName() throws Exception {
        Accept accept= new Accept();
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(accept);

        for (int i = 0; i < 100; i++) {
            final Socket socket = new Socket("localhost", accept.serverSocket.getLocalPort());
            new Temp(socket);
//            executorService.submit(new Reader(socket.getInputStream()));
            if ((i % 10) == 0){
                System.gc();
            }
        }
        System.out.println("PeerTest.testName end ");
        Thread.sleep(2000);
    }

    static class Accept implements Runnable {
        ServerSocket serverSocket;
        ExecutorService executorService = Executors.newCachedThreadPool();

        Accept() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        public void run() {
            try {
                while (true) {
                    Socket client = serverSocket.accept();
//                    client.setTcpNoDelay(true);
                    FullyReader clientRequestListener = new FullyReader(client.getInputStream());
                    executorService.execute(clientRequestListener);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class FullyReader implements Runnable {
        final InputStream inputStream;

        FullyReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public void run() {
            try {
                while (inputStream.read() != -1){
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    static class Temp{
        static List<Socket> socketList = new ArrayList<>();
        private Socket socket;

        public Temp(Socket socket) {
            this.socket = socket;
        }

        protected void finalize() throws Throwable {
            super.finalize();
            socketList.add(socket);
        }
    }

}
