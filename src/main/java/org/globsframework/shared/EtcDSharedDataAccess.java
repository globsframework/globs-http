package org.globsframework.shared;

import io.etcd.jetcd.*;
import io.etcd.jetcd.election.CampaignResponse;
import io.etcd.jetcd.election.LeaderKey;
import io.etcd.jetcd.election.LeaderResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeResolver;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.FieldValues;
import org.globsframework.core.model.Glob;
import org.globsframework.core.utils.Utils;
import org.globsframework.json.GSonUtils;
import org.globsframework.serialisation.BinReader;
import org.globsframework.serialisation.BinReaderFactory;
import org.globsframework.serialisation.BinWriterFactory;
import org.globsframework.serialisation.glob.GlobBinReader;
import org.globsframework.shared.model.PathIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class EtcDSharedDataAccess implements SharedDataAccess {
    private final static Logger LOGGER = LoggerFactory.getLogger(EtcDSharedDataAccess.class);
    private final Client client;
    private final Watch watchClient;
    private final KV kv;
    private final Lease leaseClient;
    private final GlobSerializer serializer;
    private final GlobDeserializer deserializer;
    private final String prefix;
    private final String separator;
    private final Election electionClient;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorService executorService;

    private EtcDSharedDataAccess(Client client, GlobSerializer serializer, GlobDeserializer deserializer, String prefix, String separator) {
        this.client = client;
        kv = client.getKVClient();
        watchClient = client.getWatchClient();
        leaseClient = client.getLeaseClient();
        electionClient = client.getElectionClient();
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.prefix = prefix;
        this.separator = separator;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        executorService = Executors.newCachedThreadPool();
    }

    public static SharedDataAccess createJson(Client client) {
        return createJson(client, null);
    }

    public static SharedDataAccess createJson(Client client, String prefix) {
        return createJson(client, prefix, "/");
    }

    public static SharedDataAccess createJson(Client client, String prefix, String separator) {
        GlobSerializer serializer = glob -> {
            String encode = GSonUtils.encode(glob, true);
            return encode.getBytes(StandardCharsets.UTF_8);
        };
        GlobDeserializer deserializer = resolvers -> data -> Optional.ofNullable(
                GSonUtils.decode(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), resolvers));
        return new EtcDSharedDataAccess(client, serializer, deserializer, prefix, separator);
    }

    public static SharedDataAccess createBin(Client client) {
        return createBin(client, null);
    }

    public static SharedDataAccess createBin(Client client, String prefix) {
        return createBin(client, prefix, "/");
    }

    public static SharedDataAccess createBin(Client client, String prefix, String separator) {
        final BinWriterFactory binWriterFactory = BinWriterFactory.create();
        final BinReaderFactory binReaderFactory = BinReaderFactory.create();
        GlobSerializer serializer = glob -> {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            binWriterFactory.create(outputStream).write(glob);
            return outputStream.toByteArray();
        };
        GlobDeserializer deserializer = resolvers -> {
            BinReader globBinReader = binReaderFactory.createGlobBinReader(resolvers);
            return data -> globBinReader.read(new ByteArrayInputStream(data));
        };
        return new EtcDSharedDataAccess(client, serializer, deserializer, prefix, separator);
    }

    public static String extractPath(String prefix, FieldValues glob, GlobType type, String separator) {
        List<Field> orderedField = type.streamFields().filter(field -> field.hasAnnotation(PathIndex.KEY))
                .sorted(Comparator.comparing(field1 -> field1.getAnnotation(PathIndex.KEY).get(PathIndex.index)))
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder();
        if (prefix != null) {
            builder.append(separator).append(prefix);
        }
        builder.append(separator).append(type.getName());
        int countUnset = 0;
        for (Field field : orderedField) {
            if (glob.isSet(field)) {
                for (int i = 0; i < countUnset; i++) {
                    builder.append(separator).append("null");
                }
                countUnset = 0;
                builder.append(separator).append(glob.getValue(field));
            } else {
                countUnset++;
            }
        }
        return builder.toString();
    }

    public CompletableFuture<Void> register(Glob glob) {
        GlobType type = glob.getType();
        String path = extractPath(prefix, glob, type, separator);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("register " + path);
        }
        CompletableFuture<PutResponse> put = kv.put(ByteSequence.from(path, StandardCharsets.UTF_8), ByteSequence.from(serializer.write(glob)));
        return put.thenApply(putResponse -> null);
    }

    public CompletableFuture<Void> register(Glob glob, UnLeaser unLeaser) {
        GlobType type = glob.getType();
        String path = extractPath(prefix, glob, type, separator);

        ByteSequence k = ByteSequence.from(path, StandardCharsets.UTF_8);
        ByteSequence v = ByteSequence.from(serializer.write(glob));

        return kv.put(k, v, PutOption.builder().withLeaseId(unLeaser.getLeaseId()).build()).thenApply(putResponse -> null);
    }

    public CompletableFuture<UnLeaser> registerWithLease(Glob glob, Duration duration) {
        GlobType type = glob.getType();
        String path = extractPath(prefix, glob, type, separator);

        ByteSequence k = ByteSequence.from(path, StandardCharsets.UTF_8);
        ByteSequence v = ByteSequence.from(serializer.write(glob));

        return leaseClient.grant(duration.toMillis() * 1000)
                .thenApply(LeaseGrantResponse::getID)
                .thenCompose(leaseId -> {
                    LOGGER.info("register " + path + " with lease id" + leaseId);
                    return kv.put(k, v, PutOption.builder().withLeaseId(leaseId).build())
                            .thenApply(putResponse -> new UnLeaser() {
                                public void touch() {
                                    LOGGER.debug("Touch call on " + leaseId);
                                    leaseClient.keepAliveOnce(leaseId);
                                }

                                public long getLeaseId() {
                                    return leaseId;
                                }

                                public void end() {

                                }
                            });
                });
    }

    public CompletableFuture<UnLeaser> createLease(Duration duration) {
        return leaseClient.grant(duration.toMillis() * 1000)
                .thenApply(LeaseGrantResponse::getID)
                .thenApply(leaseId -> {
                    LOGGER.info("lease " + leaseId + " created.");
                    return leaseId;
                })
                .thenApply(leaseId -> new UnLeaser() {
                    public void touch() {
                        LOGGER.info("Touch call on " + leaseId);
                        leaseClient.keepAliveOnce(leaseId);
                    }

                    public long getLeaseId() {
                        return leaseId;
                    }

                    public void end() {

                    }
                });
    }

    public CompletableFuture<UnLeaser> createAutoLease(Duration duration) {
        return createLease(duration)
                .thenApply(unLeaser -> {
                    ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(unLeaser::touch,
                            duration.toMillis() / 2, duration.toMillis() / 2, TimeUnit.MILLISECONDS);
                    return new UnLeaser() {
                        public void touch() {
                            unLeaser.touch();
                        }

                        public long getLeaseId() {
                            return unLeaser.getLeaseId();
                        }

                        public void end() {
                            scheduledFuture.cancel(false);
                        }
                    };
                });
    }

    public CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path) {
        CompletableFuture<GetResponse> getResponseCompletableFuture = kv.get(ByteSequence.from(extractPath(prefix, path, type, separator), StandardCharsets.UTF_8));
        return getResponseCompletableFuture.thenApply(getResponse -> {
            List<KeyValue> kvs = getResponse.getKvs();
            if (kvs.isEmpty()) {
                return Optional.empty();
            }
            if (kvs.size() > 1) {
                LOGGER.warn("Many value return " + kvs.size());
            }
            ByteSequence value = kvs.get(0).getValue();
            return deserializer.with(GlobTypeResolver.from(type)).read(value.getBytes());
        });
    }

    public CompletableFuture<List<Glob>> getUnder(GlobType type, FieldValues path) {
        return getUnderWithRevision(type, path).thenApplyAsync(ResultAndRevision::data, executorService);
    }

    private CompletableFuture<ResultAndRevision> getUnderWithRevision(GlobType type, FieldValues path) {
        CompletableFuture<GetResponse> getResponseCompletableFuture =
                kv.get(ByteSequence.from(extractPath(prefix, path, type, separator), StandardCharsets.UTF_8), GetOption.builder().isPrefix(true).build());
        CompletableFuture<ResultAndRevision> completableFuture = getResponseCompletableFuture.thenApply(getResponse -> {
            List<KeyValue> kvs = getResponse.getKvs();
            long revision = getResponse.getHeader().getRevision();
            if (kvs.isEmpty()) {
                return new ResultAndRevision(Collections.emptyList(), revision);
            }
            List<Glob> data = new ArrayList<>();
            for (KeyValue keyValue : kvs) {
                ByteSequence value = keyValue.getValue();
                deserializer.with(GlobTypeResolver.from(type)).read(value.getBytes())
                        .ifPresent(data::add);
            }
            return new ResultAndRevision(data, revision);
        });
        completableFuture.exceptionally(throwable -> {
            LOGGER.error("Exception thrown", throwable);
            return null;
        });
        return completableFuture;
    }

    public CompletableFuture<ListenerCtrl> getAndListenUnder(GlobType type, FieldValues path, InitialLoad pastData, Listener newData) {
        return getUnderWithRevision(type, path)
                .thenComposeAsync(resultAndRevision -> {
                    try {
                        return pastData.accept(resultAndRevision.data)
                                .thenApply(unused -> resultAndRevision);
                    } catch (Exception e) {
                        LOGGER.error("Ignored exception : ", e);
                        return CompletableFuture.completedFuture(resultAndRevision);
                    }
                }, executorService)
                .thenApply(resultAndRevision -> resultAndRevision.revision + 1)
                .thenApply(revision -> listenUnder(type, newData, path, revision));
    }

    public ListenerCtrl listen(GlobType type, Listener listener, FieldValues orderedPath) {
        Listener logListener = new LoggerListener(listener);
        GlobDeserializer.Deserializer globBinReader = deserializer.with(GlobTypeResolver.from(type));
        watchClient.watch(ByteSequence.from(extractPath(prefix, orderedPath, type, separator), StandardCharsets.UTF_8),
                WatchOption.builder()
                        .withPrevKV(true)
                        .build(),
                watchResponse -> executorService.submit(() -> {
                    try {
                        for (WatchEvent event : watchResponse.getEvents()) {
                            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                                globBinReader.read(event.getPrevKV().getValue().getBytes())
                                        .ifPresent(logListener::delete);
                            } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                                globBinReader.read(event.getKeyValue().getValue().getBytes())
                                        .ifPresent(logListener::put);
                            } else {
                                LOGGER.warn("event not unrecognized");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception in watch callback", e);
                    }
                }));
        return new ListenerCtrl() {
            public void close() {
                LOGGER.info("Close call on " + orderedPath);
                watchClient.close();
            }
        };
    }

    public ListenerCtrl listenUnder(GlobType type, Listener listener, FieldValues orderedPath) {
        return listenUnder(type, listener, orderedPath, -1);
    }

    public ListenerCtrl listenUnder(GlobType type, Listener listener, FieldValues orderedPath, long startAtRevision) {
        LOGGER.info("listenUnder " + orderedPath);
        Listener logListener = new LoggerListener(listener);
        GlobDeserializer.Deserializer globBinReader = deserializer.with(GlobTypeResolver.from(type));
        WatchOption.Builder option = WatchOption.builder()
                .withPrevKV(true)
                .isPrefix(true);
        if (startAtRevision != -1) {
            option.withRevision(startAtRevision);
        }
        Watch.Watcher watch = watchClient.watch(ByteSequence.from(extractPath(prefix, orderedPath, type, separator), StandardCharsets.UTF_8), option.build(),
                watchResponse -> executorService.submit(() -> {
                    try {
                        for (WatchEvent event : watchResponse.getEvents()) {
                            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                                globBinReader.read(event.getPrevKV().getValue().getBytes())
                                        .ifPresent(logListener::delete);
                            } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                                globBinReader.read(event.getKeyValue().getValue().getBytes())
                                        .ifPresent(logListener::put);
                            } else {
                                LOGGER.warn("event not unrecognized");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception in watch callback", e);
                    }
                }));
        return () -> {
            LOGGER.info("Close call on " + orderedPath);
            watch.close();
        };
    }

    public CompletableFuture<Void> delete(GlobType type, FieldValues values) {
        String source = extractPath(prefix, values, type, separator);
        LOGGER.info("Delete call on " + source);
        return kv.delete(ByteSequence.from(source, StandardCharsets.UTF_8))
                .whenComplete((deleteResponse, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("delete on error " + type.getName() + " => " + source, throwable);
                    }
                })
                .thenAccept(deleteResponse -> {
                });
    }

    public CompletableFuture<LeaderOperation> registerForLeaderShip(Glob glob, LeaderListener listener) {
        CompletableFuture<LeaseGrantResponse> grant = leaseClient.grant(1);
        String key = extractPath(prefix, glob, glob.getType(), separator);
        byte[] value = serializer.write(glob);
        return grant.thenApply(leaseGrantResponse -> {
            long leaseId = leaseGrantResponse.getID();
            LOGGER.info(key + " registered with leaseId " + leaseId);
            ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> leaseClient.keepAliveOnce(leaseId),
                    500, 700, TimeUnit.MILLISECONDS);
            return new ListenerAndLeaderOperation(ByteSequence.from(key, StandardCharsets.UTF_8), ByteSequence.from(value),
                    leaseId, listener, scheduledFuture);
        });
    }

    public void end() {
        LOGGER.info("etcd end");
        client.close();
        scheduledExecutorService.shutdown();
        executorService.shutdown();
    }

    interface GlobSerializer {
        byte[] write(Glob glob);
    }

    interface GlobDeserializer {
        Deserializer with(GlobTypeResolver resolvers);

        interface Deserializer {
            Optional<Glob> read(byte[] data);
        }
    }

    static class ResultAndRevision{
        private final List<Glob> data;
        private final long revision;

        ResultAndRevision(List<Glob> data, long revision) {
            this.data = data;
            this.revision = revision;
        }
        public List<Glob> data() {
            return data;
        }
        public long revision() {
            return revision;
        }
    }

    private static class LoggerListener implements Listener {
        private final Listener listener;

        public LoggerListener(Listener listener) {
            this.listener = listener;
        }

        public void put(Glob glob) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Receive put " + GSonUtils.encode(glob, true));
            }
            try {
                listener.put(glob);
            } catch (Exception e) {
                LOGGER.error("Got exception", e);
            }
        }

        public void delete(Glob glob) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Receive delete " + GSonUtils.encode(glob, true));
            }
            try {
                listener.delete(glob);
            } catch (Exception e) {
                LOGGER.error("Got exception", e);
            }
        }
    }

    private class ListenerAndLeaderOperation implements LeaderOperation, Election.Listener {
        private final ByteSequence electionName;
        private final ByteSequence value;
        private final long leaseId;
        private final LeaderListener listener;
        private final ScheduledFuture<?> scheduledFuture;
        private CompletableFuture<CampaignResponse> campaign;
        private CompletableFuture<LeaderKey> leaderKeyCompletableFuture;

        public ListenerAndLeaderOperation(ByteSequence electionName, ByteSequence value, long leaseId, LeaderListener listener, ScheduledFuture<?> scheduledFuture) {
            this.electionName = electionName;
            this.value = value;
            this.leaseId = leaseId;
            this.listener = listener;
            this.scheduledFuture = scheduledFuture;
        }

        void init() {
            electionClient.observe(electionName, this);
            campaign = electionClient.campaign(electionName, leaseId, value);
            leaderKeyCompletableFuture = campaign.thenApply(campaignResponse -> {
                LOGGER.info("I am the leader for " + electionName);
                listener.youAreTheLeader();
                return campaignResponse.getLeader();
            });
        }

        synchronized public void releaseMyLeaderShip() {
            LOGGER.info("release wanted on " + electionName);
            if (leaderKeyCompletableFuture.isDone()) {
                listener.youAreNotTheLeaderAnyMore();
                electionClient.resign(leaderKeyCompletableFuture.join());
                Utils.sleep(1000); //force wait to allow a new leader different then this.
                init();
            }
        }

        synchronized public void shutDown() {
            LOGGER.info("shutDown wanted on " + electionName);
            releaseMyLeaderShip();
            scheduledFuture.cancel(false);
            leaseClient.revoke(leaseId);
        }

        public void onNext(LeaderResponse response) {
            LOGGER.debug("onNext call on " + electionName);
            if (!response.getKv().getValue().equals(value)) {
                LOGGER.info("Force release ");
                releaseMyLeaderShip();
            } else {
                LOGGER.debug("Same leader.");
            }
        }

        synchronized public void onError(Throwable throwable) {
            LOGGER.error("onError call on " + electionName, throwable);
            releaseMyLeaderShip();
        }

        public void onCompleted() {
            LOGGER.info("onCompleted call on " + electionName);
        }
    }
}
