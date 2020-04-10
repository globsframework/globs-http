package org.globsframework.remote;

public interface SharedService extends SharedInOut {

    void reset();

    long newUniqueId();
}
