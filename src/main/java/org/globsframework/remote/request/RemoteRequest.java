package org.globsframework.remote.request;

import org.globsframework.utils.serialization.SerializedOutput;

public interface RemoteRequest extends Request {

    void read(SerializedOutput response);
}
