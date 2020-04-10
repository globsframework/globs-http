package org.globsframework.remote.peer;

import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.utils.Files;
import org.globsframework.utils.NanoChrono;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerClient {

    public static void main(String[] args) throws Exception {
        final PeerToPeer peer = new DirectPeerToPeer();

        final String url = args[0];
        if (args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                PeerToPeer.ClientRequestFactory clientRequestFactory = peer.getClientRequestFactory(url);
                PeerServer.AccesibleByteArrayOutputStream byteArrayOutputStream = new PeerServer.AccesibleByteArrayOutputStream();
                Files.copyFileToOutputStream(arg, byteArrayOutputStream);
                long length = new File(arg).length();
                for (int j = 0; j < 3; j++) {
                    PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
                    OutputStream requestStream = clientRequest.getRequestStream();
                    NanoChrono chrono = NanoChrono.start();
                    Files.copy(new ByteArrayInputStream(byteArrayOutputStream.getBuffer(), 0, byteArrayOutputStream.size()), requestStream);
                    clientRequest.requestComplete();
//                    InputStream complete = clientRequest.getResponseInputStream();
                    int elapsedTime = (int) chrono.getElapsedTimeInMS();
//                    chrono.reset();
//                    CountOutputStream outputStream = new CountOutputStream();
//                    Files.copy(complete, outputStream);
//                    elapsedTime = chrono.getElapsedTime();
//                    System.out.println("client receive " + outputStream.i + " in " + elapsedTime + " ms " + (length * 1000 / 1024 / 1024 / elapsedTime) + " mg/s");
                    clientRequest.end();
                    System.out.println("client data sent " + length + " in " + elapsedTime + " ms " + (length * 1000 / 1024 / 1024 / elapsedTime) + " mg/s");
                }
                clientRequestFactory.release();
            }
        } else {
            ExecutorService executorService = Executors.newFixedThreadPool(20);
            for (int i = 0; i < 1000; i++) {
                executorService.execute(new Runnable() {
                    public void run() {
                        PeerTest.Client client = new PeerTest.Client(peer, url);
                        try {
                            client.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.DAYS);
        }
        peer.destroy();
    }

    private static class CountOutputStream extends OutputStream {
        int i = 0;

        public void write(int b) throws IOException {
            ++i;
        }
    }
}
