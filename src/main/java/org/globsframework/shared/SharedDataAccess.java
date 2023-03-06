package org.globsframework.shared;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.FieldValues;
import org.globsframework.model.Glob;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface SharedDataAccess {

    CompletableFuture<Void> register(Glob glob);

    CompletableFuture<Void> register(Glob glob, UnLeaser unLeaser);

    default CompletableFuture<UnLeaser> registerWithLease(Glob glob, int timeOut, TimeUnit unit){
        return registerWithLease(glob, Duration.ofSeconds(unit.toSeconds(timeOut)));
    }

    CompletableFuture<UnLeaser> registerWithLease(Glob glob, Duration duration);

    CompletableFuture<UnLeaser> createLease(Duration duration);

    CompletableFuture<UnLeaser> createAutoLease(Duration duration);

//    CompletableFuture<UnLeaser> registerWithAutoLease(Glob glob, int timeOut, TimeUnit unit);

    CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path);

    CompletableFuture<List<Glob>> getUnder(GlobType type, FieldValues path);

    default CompletableFuture<ListenerCtrl> getAndListenUnder(GlobType type, FieldValues path, Consumer<List<Glob>> pastData, Listener newData) {
        return getAndListenUnder(type, path, globs -> {
            pastData.accept(globs);
            return CompletableFuture.completedFuture(null);
        }, newData);
    }

    CompletableFuture<ListenerCtrl> getAndListenUnder(GlobType type, FieldValues path, InitialLoad pastData, Listener newData);

    default ListenerCtrl listen(GlobType type, Listener listener) {
        return listen(type, listener, FieldValues.EMPTY);
    }

    default ListenerCtrl listenUnder(GlobType type, Listener listener) {
        return listenUnder(type, listener, FieldValues.EMPTY);
    }

    ListenerCtrl listen(GlobType type, Listener listener, FieldValues orderedPath);

    ListenerCtrl listenUnder(GlobType type, Listener listener, FieldValues orderedPath);

    CompletableFuture<Void> delete(GlobType type, FieldValues values);

    CompletableFuture<LeaderOperation> registerForLeaderShip(Glob glob, LeaderListener listener);

    interface LeaderOperation {
        void releaseMyLeaderShip();

        void shutDown();
    }

    interface LeaderListener {
        void youAreTheLeader();

        void youAreNotTheLeaderAnyMore();
    }

    interface InitialLoad{
        CompletableFuture<Void> accept(List<Glob> globs);
    }

    interface ListenerCtrl {
        void close();
    }

    interface UnLeaser {
        void touch();

        long getLeaseId();

        void end();
    }

    interface Listener {
        void put(Glob glob);
        void delete(Glob glob);
    }

    void end();
}
