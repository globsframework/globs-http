package org.globsframework.remote.peer.direct;

public enum MessageType {
    START(1), END(2), CONTENT(3), CANCEL(4), START_END(5), PING(6), WAIT(7);
    public final int id;

    MessageType(int id) {
        this.id = id;
    }
}
