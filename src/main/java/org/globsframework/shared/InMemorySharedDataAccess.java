package org.globsframework.shared;

import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.FieldValues;
import org.globsframework.model.Glob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class InMemorySharedDataAccess implements SharedDataAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemorySharedDataAccess.class);
    private final Map<String, Glob> paths = new ConcurrentHashMap<>();
    private final List<SimpleListener> listeners = new ArrayList<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong id = new AtomicLong(0);
    private final Map<Long, UnLeaser> leasers = new ConcurrentHashMap<>();
    private final String prefix;

    public InMemorySharedDataAccess() {
        this(null);
    }

    public InMemorySharedDataAccess(String prefix) {
        this.prefix = prefix;
    }

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
        String path = EtcDSharedDataAccess.extractPath(prefix, glob, glob.getType(), "/");
        paths.put(path, glob.duplicate());
        for (SimpleListener listener : listeners) {
            listener.putOn(path, glob);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<UnLeaser> registerWithLease(Glob glob, Duration duration) {
        register(glob);
        long key = id.incrementAndGet();
        InMemoryUnLeaser value = new InMemoryUnLeaser(glob, key, duration);
        leasers.put(key, value);
        return CompletableFuture.completedFuture(value);
    }

    public UnLeaser getUnleaser(long leaseId) {
        return leasers.get(leaseId);
    }

    public CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path) {
        String p = EtcDSharedDataAccess.extractPath(prefix, path, type, "/");
        return CompletableFuture.completedFuture(Optional.ofNullable(paths.get(p)));
    }

    public CompletableFuture<List<Glob>> getUnder(GlobType type, FieldValues path) {
        String p = EtcDSharedDataAccess.extractPath(prefix, path, type, "/");
        List<Glob> globs = new ArrayList<>();
        for (Map.Entry<String, Glob> stringGlobEntry : paths.entrySet()) {
            if (stringGlobEntry.getKey().startsWith(p)) {
                globs.add(stringGlobEntry.getValue());
            }
        }
        return CompletableFuture.completedFuture(globs);
    }

    public CompletableFuture<ListenerCtrl> getAndListenUnder(GlobType type, FieldValues path, Consumer<List<Glob>> pastData, Listener newData) {
        CompletableFuture<List<Glob>> under = getUnder(type, path);
        CompletableFuture<ListenerCtrl> listenerCtrlCompletableFuture = CompletableFuture.completedFuture(listenUnder(type, newData));
        under.thenAccept(pastData);
        listenerCtrlCompletableFuture.exceptionally(throwable -> {
            LOGGER.error("unexpected exception", throwable);
            return null;
        });
        return listenerCtrlCompletableFuture;
    }

    public ListenerCtrl listen(GlobType type, Listener listener, FieldValues orderedPath) {
        String p = EtcDSharedDataAccess.extractPath(prefix, orderedPath, type, "/");
        SimpleListener e = new SimpleListener(listener, p, false);
        listeners.add(e);
        return () -> listeners.remove(e);
    }

    public ListenerCtrl listenUnder(GlobType type, Listener listener, FieldValues orderedPath) {
        String p = EtcDSharedDataAccess.extractPath(prefix, orderedPath, type, "/");
        SimpleListener e = new SimpleListener(listener, p, true);
        listeners.add(e);
        return () -> listeners.remove(e);
    }

    public CompletableFuture<Void> delete(GlobType type, FieldValues values) {
        String p = EtcDSharedDataAccess.extractPath(prefix, values, type, "/");
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

    public void end() {

    }

    private class InMemoryUnLeaser implements UnLeaser, Callable<Void> {
        private  ScheduledFuture<?> schedule;
        private Duration duration;
        private Glob glob;
        long id;

        public InMemoryUnLeaser(Glob glob, long id, Duration duration) {
            this.glob = glob;
            this.id = id;
            this.schedule = scheduledExecutorService.schedule(this, duration.toSeconds(), TimeUnit.SECONDS);
            this.duration = duration;
        }

        public void touch() {
            schedule.cancel(false);
            schedule = scheduledExecutorService.schedule(this, duration.toSeconds(), TimeUnit.SECONDS);
        }

        public long getLeaseId() {
            return id;
        }

        public Void call() throws Exception {
            if (glob != null) {
                LOGGER.info("timeout deleting " + GSonUtils.encode(glob, true));
                delete(glob.getType(), glob);
                leasers.remove(id);
            }
            return null;
        }
    }
}
