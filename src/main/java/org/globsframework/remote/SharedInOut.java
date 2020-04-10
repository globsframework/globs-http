package org.globsframework.remote;

import org.globsframework.remote.request.Shared;
import org.globsframework.utils.serialization.SerializedInput;

public interface SharedInOut {
    <T> Shared<T> declare(T value);

    <T> Shared<T> restoreShared(SerializedInput input, T t);
}
