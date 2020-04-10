package org.globsframework.remote.peer;

import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.utils.Files;
import org.globsframework.utils.NanoChrono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PeerServer {
    static {
    }

    public static void main(String[] args) {

        final PeerToPeer peer = new DirectPeerToPeer();
        final ServerListener serverListener = peer.createServerListener(2450, 10, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        try {
//                            final AccesibleByteArrayOutputStream tmp = new AccesibleByteArrayOutputStream();
                            NanoChrono chrono = NanoChrono.start();
//                            final OutputStream responseStream = response.getResponseStream();
                            SizeOutputStream outputStream = new SizeOutputStream(null);
                            Files.copy(serverRequest.getRequestStream(), outputStream);
//                            int length = tmp.size();
//                            long elapsedTime = chrono.getElapsedTime();
//                            System.out.println("server receive " + length + " bytes in " + elapsedTime + " ms " + (length * 1000l / 1024l / 1024l / elapsedTime) + " mg/s");
//                            chrono.reset();
//                            Files.copy(new ByteArrayInputStream(tmp.getBuffer(), 0, length), response.getResponseStream());
                            long elapsedTime = (long) chrono.getElapsedTimeInMS();
                            System.out.println("server send back " + outputStream.length + " bytes in " + elapsedTime + " ms " + (outputStream.length * 1000l / 1024l / 1024l / elapsedTime) + " mg/s");
//                            tmp.reset();
                        } catch (IOException e) {
                            throw new RuntimeException("input => output", e);
                        } finally {
                            response.complete();
                        }
                    }
                };
            }
        }, "P2P");

        System.out.println("Server.main wait on " + serverListener.getUrl());

        serverListener.join();
        peer.destroy();
    }

    public static class AccesibleByteArrayOutputStream extends ByteArrayOutputStream {
        public AccesibleByteArrayOutputStream(int size) {
            super(size);
        }

        public AccesibleByteArrayOutputStream() {
            super(200 * 1024 * 1024);
        }

        public byte[] getBuffer(){
            return buf;
        }
    }

    private static class SizeOutputStream extends OutputStream {
        private final OutputStream responseStream;
        private int length;

        public SizeOutputStream(OutputStream responseStream) {
            this.responseStream = responseStream;
        }

        public void write(int b) throws IOException {
            length++;
            //responseStream.write(b);
        }
    }
}
