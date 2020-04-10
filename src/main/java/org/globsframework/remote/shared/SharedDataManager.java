package org.globsframework.remote.shared;

import org.globsframework.metamodel.GlobModel;

public interface SharedDataManager {

    void create(Path path, GlobModel model) throws AlreadyExist;

    SharedDataService getSharedDataService(Path path, GlobModel model);

    SharedDataService getSharedDataServiceSync(Path path, GlobModel model);

    Path[] listService(Path parent);

    void close();

    // a string with a / for separator
    interface Path {
        String getFullPath();

        int getElementCount();
    }

    class AlreadyExist extends RuntimeException {
        public AlreadyExist(String message) {
            super(message);
        }
    }

}
