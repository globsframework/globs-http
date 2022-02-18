package org.globsframework.shared;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.FieldValues;
import org.globsframework.model.Glob;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface SharedDataAccess {

    CompletableFuture<Void> register(Glob glob);

    CompletableFuture<UnLeaser> registerWithLease(Glob glob, int timeOut, TimeUnit unit);

    CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path);

    CompletableFuture<List<Glob>> getUnder(GlobType type, FieldValues path);

    default void listen(GlobType type, Listener listener) {
        listen(type, listener, FieldValues.EMPTY);
    }

    default void listenUnder(GlobType type, Listener listener) {
        listenUnder(type, listener, FieldValues.EMPTY);
    }

    void listen(GlobType type, Listener listener, FieldValues orderedPath);

    void listenUnder(GlobType type, Listener listener, FieldValues orderedPath);

    CompletableFuture<Void> delete(GlobType type, FieldValues values);

    interface UnLeaser {
        void touch();
    }

    interface Listener {
        void put(Glob glob);
        void delete(Glob glob);
    }

    void end();
}
