package org.globsframework.remote;

import org.globsframework.remote.request.Shared;
import org.globsframework.utils.serialization.SerializedOutput;

public class DefaultGloballyShared<T> implements Shared<T> {
    final T t;
    final int serverId;
    final int objectId;

    public DefaultGloballyShared(T t, int serverId, int objectId) {
        this.t = t;
        this.serverId = serverId;
        this.objectId = objectId;
    }

    public T get() {
        return t;
    }

    public void postSave(SerializedOutput serializedOutput) {
        serializedOutput.write(serverId);
        serializedOutput.write(objectId);
    }
}
