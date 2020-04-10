package org.globsframework.remote.peer;

import java.io.InputStream;
import java.io.OutputStream;

public interface PeerToPeer {

    ServerListener createServerListener(int threadCount, ServerRequestFactory serverRequestFactory, String name);

    ServerListener createServerListener(int port, int threadCount, ServerRequestFactory serverRequestFactory, String name);

    ClientRequestFactory getClientRequestFactory(String url);

    void destroy();

    interface ClientRequest {
        OutputStream getRequestStream();

        InputStream getResponseInputStream();

        // The request is fully sent.
        void requestComplete();

        // the result is fully read.
        void end();
    }

    interface ClientRequestFactory {
        ClientRequest create();

        void release();
    }

    interface ServerRequestFactory {
        ServerRequestProcessor createServerRequest();
    }

    interface ServerRequest {

        InputStream getRequestStream();
    }

    interface ServerRequestProcessor {
        void receive(ServerRequest serverRequest, ServerResponseBuilder response);
    }


    interface ServerResponseBuilder {

        OutputStream getResponseStream();

    //    OutputStream getSecondaryStream();

        void complete();
    }
}
