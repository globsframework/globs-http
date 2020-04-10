package org.globsframework.remote.request;

import org.globsframework.utils.serialization.SerializedOutput;

public interface Shared<T> {

    T get();

    void postSave(SerializedOutput output);
}
