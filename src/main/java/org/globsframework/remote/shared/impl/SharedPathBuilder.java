package org.globsframework.remote.shared.impl;

import org.globsframework.remote.shared.SharedDataManager;

public class SharedPathBuilder {
    static public SharedDataManager.Path create(String path){
        return new SharedPathImpl(path);
    }
}
