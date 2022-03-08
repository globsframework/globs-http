package org.globsframework.shared;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.FieldValues;
import org.globsframework.model.Glob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class InMemorySharedDataAccess implements SharedDataAccess {
    private final Map<String, Glob> paths = new ConcurrentHashMap<>();
    private final List<SimpleListener> listeners = new ArrayList<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong id = new AtomicLong(0);
    private final Map<Long, UnLeaser> leasers = new ConcurrentHashMap<>();

    public static class SimpleListener {
        private final Listener listener;
        private final String path;
        private final boolean allUnder;

        public SimpleListener(Listener listener, String path, boolean allUnder) {
            this.listener = listener;
            this.path = path;
            this.allUnder = allUnder;
        }

        public void putOn(String path, Glob glob) {
            if (allUnder) {
                if (path.startsWith(this.path)) {
                    listener.put(glob);
                }
            }
            else {
                if (path.equals(this.path)) {
                    listener.put(glob);
                }
            }
        }

        public void delete(String path, Glob glob) {
            if (allUnder) {
                if (path.startsWith(this.path)) {
                    listener.delete(glob);
                }
            }
            else {
                if (path.equals(this.path)) {
                    listener.delete(glob);
                }
            }
        }
    }

    public CompletableFuture<Void> register(Glob glob) {
        String path = EtcDSharedDataAccess.extractPath(glob, glob.getType());
        paths.put(path, glob.duplicate());
        for (SimpleListener listener : listeners) {
            listener.putOn(path, glob);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<UnLeaser> registerWithLease(Glob glob, int timeOut, TimeUnit unit) {
        register(glob);
        long key = id.incrementAndGet();
        InMemoryUnLeaser value = new InMemoryUnLeaser(glob, key, timeOut, unit);
        leasers.put(key, value);
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public UnLeaser getUnleaser(long leaseId) {
        return leasers.get(leaseId);
    }

    @Override
    public CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path) {
        String p = EtcDSharedDataAccess.extractPath(path, type);
        return CompletableFuture.completedFuture(Optional.ofNullable(paths.get(p)));
    }

    @Override
    public CompletableFuture<List<Glob>> getUnder(GlobType type, FieldValues path) {
        String p = EtcDSharedDataAccess.extractPath(path, type);
        List<Glob> globs = new ArrayList<>();
        for (Map.Entry<String, Glob> stringGlobEntry : paths.entrySet()) {
            if (stringGlobEntry.getKey().startsWith(p)) {
                globs.add(stringGlobEntry.getValue());
            }
        }
        return CompletableFuture.completedFuture(globs);
    }

    @Override
    public CompletableFuture<ListenerCtrl> getAndListenUnder(GlobType type, FieldValues path, Consumer<List<Glob>> pastData, Listener newData) {
        CompletableFuture<List<Glob>> under = getUnder(type, path);
        CompletableFuture<ListenerCtrl> listenerCtrlCompletableFuture = CompletableFuture.completedFuture(listenUnder(type, newData));
        under.thenAccept(pastData);
        return listenerCtrlCompletableFuture;
    }

    @Override
    public ListenerCtrl listen(GlobType type, Listener listener, FieldValues orderedPath) {
        String p = EtcDSharedDataAccess.extractPath(orderedPath, type);
        SimpleListener e = new SimpleListener(listener, p, false);
        listeners.add(e);
        return () -> listeners.remove(e);
    }

    @Override
    public ListenerCtrl listenUnder(GlobType type, Listener listener, FieldValues orderedPath) {
        String p = EtcDSharedDataAccess.extractPath(orderedPath, type);
        SimpleListener e = new SimpleListener(listener, p, true);
        listeners.add(e);
        return () -> listeners.remove(e);
    }

    @Override
    public CompletableFuture<Void> delete(GlobType type, FieldValues values) {
        String p = EtcDSharedDataAccess.extractPath(values, type);
        Glob glob = paths.remove(p);
        if (glob != null) {
            for (SimpleListener listener : listeners) {
                listener.delete(p, glob);
            }
            return CompletableFuture.completedFuture(null);
        }
        else {
            return CompletableFuture.failedFuture(new RuntimeException(p + " not found."));
        }
    }

    @Override
    public void end() {

    }

    private class InMemoryUnLeaser implements UnLeaser, Callable<Void> {
        private  ScheduledFuture<?> schedule;
        private int timeOut;
        private TimeUnit unit;
        private Glob glob;
        long id;

        public InMemoryUnLeaser(Glob glob, long id, int timeOut, TimeUnit unit) {
            this.glob = glob;
            this.id = id;
            this.schedule = scheduledExecutorService.schedule(this, timeOut, unit);
            this.timeOut = timeOut;
            this.unit = unit;
        }

        public void touch() {
            schedule.cancel(false);
            schedule = scheduledExecutorService.schedule(this, timeOut, unit);
        }

        public long getLeaseId() {
            return id;
        }

        public Void call() throws Exception {
            if (glob != null) {
                delete(glob.getType(), glob);
                leasers.remove(id);
            }
            return null;
        }
    }
}
