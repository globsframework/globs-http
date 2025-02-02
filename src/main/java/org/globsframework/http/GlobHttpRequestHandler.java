package org.globsframework.http;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public interface GlobHttpRequestHandler {

    void callHandler();

    void streamEnd(List<? extends Header> trailers);

    void consumeRequest(ByteBuffer src);

    void produceResponse(DataStreamChannel channel) throws IOException;

    int availableInResponse();

    void releaseResources();

    void updateCapacityToReceiveData(CapacityChannel capacityChannel);

    void failed(Exception cause);
}
