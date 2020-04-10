package org.globsframework.remote.shared;

import org.globsframework.model.ChangeSet;

public abstract class AbstractSharedDataEventListener implements SharedDataService.SharedDataEventListener {
    public void event(ChangeSet changeSet) {
        reset();
    }
}
