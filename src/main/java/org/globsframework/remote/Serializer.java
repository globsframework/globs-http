package org.globsframework.remote;

import org.globsframework.directory.Directory;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedOutput;

public interface Serializer<T> {
    Class<T> getClassType();

    T read(SerializedInput serializedInput, Directory directory);

    void write(T object, SerializedOutput serializedOutput);
}
