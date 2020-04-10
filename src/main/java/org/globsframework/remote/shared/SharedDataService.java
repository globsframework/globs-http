package org.globsframework.remote.shared;

import org.globsframework.model.ChangeSet;
import org.globsframework.model.GlobRepository;

public interface SharedDataService {

    int getId();  //unique across shared data ; can change between re-connection to shared repo. !!!!should be access only from read/write

    <T extends SharedData> T read(T sharedData);

    <T extends SharedData> T write(T sharedData);

    void listen(SharedDataEventListener sharedDataEventListener);

    void remove(SharedDataEventListener sharedDataEventListener);

    void stop();

    boolean waitForInitialization(int timeoutInMilliS);

    interface SharedData {
        void data(GlobRepository globRepository);
    }

    interface SharedDataEventListener {
        void event(ChangeSet changeSet); // call from the unique thread that receive changeSet.

        void reset();
    }
}
