package org.globsframework.remote.shared;

import org.globsframework.metamodel.GlobType;

public interface ServerSharedData {
    void stop();

    int getPort();

    String getHost();

    void addGlobTypes(GlobType... globTypes);
}
