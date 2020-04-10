package org.globsframework.remote.peer;

public interface ServerListener {
    String getUrl();

    void join();

    void stop();
}
