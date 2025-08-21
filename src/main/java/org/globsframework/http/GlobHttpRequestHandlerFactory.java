package org.globsframework.http;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

public interface GlobHttpRequestHandlerFactory {
    GlobHttpRequestHandler create(HttpRequest request, EntityDetails entityDetails, ResponseChannel responseChannel, HttpContext context);
}
