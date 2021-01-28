package org.globsframework.http;

import org.globsframework.metamodel.GlobType;

public interface HttpReceiver {

    String getUrl();

    GlobType getUrlType();

    HttpOperation[] getOperations();

    void headers(HttpOperation.HeaderConsumer headerConsumer);
}
