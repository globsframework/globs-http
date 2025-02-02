package org.globsframework.http;

import org.apache.hc.core5.http.Header;

import java.nio.ByteBuffer;
import java.util.List;

public interface IGlobHttpRequestHandler {
    void consume(ByteBuffer src);

    void streamEnd(List<? extends Header> trailers);
}
