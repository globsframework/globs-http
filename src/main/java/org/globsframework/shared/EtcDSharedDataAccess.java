package org.globsframework.shared;

import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeResolver;
import org.globsframework.model.FieldValues;
import org.globsframework.model.Glob;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    interface GlobSerializer {
        byte[] write(Glob glob);
    }

    interface GlobDeserializer {
        Deserializer with(GlobTypeResolver resolvers);
        interface Deserializer{
            Optional<Glob> read(byte[] data);
        }
    }

    public static SharedDataAccess createJson(Client client) {
        GlobSerializer serializer = glob -> {
            String encode = GSonUtils.encode(glob, true);
            return encode.getBytes(StandardCharsets.UTF_8);
        };
        GlobDeserializer deserializer = resolvers -> data -> Optional.ofNullable(
                GSonUtils.decode(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), resolvers));
        return new EtcDSharedDataAccess(client, serializer, deserializer);
    }

    public static SharedDataAccess createBin(Client client) {
        final BinWriterFactory binWriterFactory = BinWriterFactory.create();
        final BinReaderFactory binReaderFactory = BinReaderFactory.create();
        GlobSerializer serializer = glob -> {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            binWriterFactory.create(outputStream).write(glob);
            return outputStream.toByteArray();
        };
        GlobDeserializer deserializer = resolvers -> {
            GlobBinReader globBinReader = binReaderFactory.createGlobBinReader(resolvers);
            return data -> globBinReader.read(new ByteArrayInputStream(data));
        };
        return new EtcDSharedDataAccess(client, serializer, deserializer);
    }

    private EtcDSharedDataAccess(Client client, GlobSerializer serializer, GlobDeserializer deserializer) {
        this.client = client;
        kv = client.getKVClient();
        watchClient = client.getWatchClient();
        leaseClient = client.getLeaseClient();
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    public CompletableFuture<Void> register(Glob glob) {
        GlobType type = glob.getType();
        String path = extractPath(glob, type);
        CompletableFuture<PutResponse> put = kv.put(ByteSequence.from(path, StandardCharsets.UTF_8), ByteSequence.from(serializer.write(glob)));
        return put.thenApply(putResponse -> null);
    }

    private String extractPath(FieldValues glob, GlobType type) {
        List<Field> orderedField = type.streamFields().filter(field -> field.hasAnnotation(PathIndex.KEY))
                .sorted(Comparator.comparing(field1 -> field1.getAnnotation(PathIndex.KEY).get(PathIndex.index)))
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder();
        builder.append(type.getName()).append(":");
        int countUnset = 0;
        for (Field field : orderedField) {
            if (glob.isSet(field)) {
                for (int i = 0; i < countUnset; i++) {
                    builder.append("null.");
                }
                builder.append(glob.getValue(field))
                        .append(".");
            }
            else {
                countUnset++;
            }
        }
        return builder.substring(0, builder.length() -1);
    }

    public CompletableFuture<UnLeaser> registerWithLease(Glob glob, int timeOut, TimeUnit unit) {
        GlobType type = glob.getType();
        String path = extractPath(glob, type);

        ByteSequence k = ByteSequence.from(path, StandardCharsets.UTF_8);
        ByteSequence v = ByteSequence.from(serializer.write(glob));

        return leaseClient.grant(unit.toSeconds(timeOut))
                .thenApply(LeaseGrantResponse::getID)
                .thenCompose(leaseId ->
                        kv.put(k, v, PutOption.newBuilder().withLeaseId(leaseId).build())
                        .thenApply(putResponse -> new UnLeaser() {
                            public void touch() {
                                leaseClient.keepAliveOnce(leaseId);
                            }

                            public long getLeaseId() {
                                return leaseId;
                            }
                        }));
    }

    public UnLeaser getUnleaser(long leaseId) {
        return new UnLeaser() {
            public void touch() {
                leaseClient.keepAliveOnce(leaseId);
            }
            public long getLeaseId() {
                return leaseId;
            }
        };
    }

    public CompletableFuture<Optional<Glob>> get(GlobType type, FieldValues path) {
        CompletableFuture<GetResponse> getResponseCompletableFuture = kv.get(ByteSequence.from(extractPath(path, type), StandardCharsets.UTF_8));
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
        CompletableFuture<GetResponse> getResponseCompletableFuture =
                kv.get(ByteSequence.from(extractPath(path, type), StandardCharsets.UTF_8), GetOption.newBuilder().isPrefix(true).build());
        return getResponseCompletableFuture.thenApply(getResponse -> {
            List<KeyValue> kvs = getResponse.getKvs();
            if (kvs.isEmpty()) {
                return List.of();
            }
            List<Glob> data = new ArrayList<>();
            for (KeyValue keyValue : kvs) {
                ByteSequence value = keyValue.getValue();
                deserializer.with(GlobTypeResolver.from(type)).read(value.getBytes())
                        .ifPresent(data::add);
            }
            return data;
        });
    }

    public void listen(GlobType type, Listener listener, FieldValues orderedPath) {
        Listener logListener = new LoggerListener(listener);
        GlobDeserializer.Deserializer globBinReader = deserializer.with(GlobTypeResolver.from(type));
        watchClient.watch(ByteSequence.from(extractPath(orderedPath, type), StandardCharsets.UTF_8),
                WatchOption.newBuilder()
                        .withPrevKV(true)
                        .build(),
                watchResponse -> {
                    try {
                        for (WatchEvent event : watchResponse.getEvents()) {
                            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                                globBinReader.read(event.getPrevKV().getValue().getBytes())
                                        .ifPresent(logListener::delete);
                            } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                                globBinReader.read(event.getKeyValue().getValue().getBytes())
                                        .ifPresent(logListener::put);
                            } else {
                                LOGGER.info("event not unrecognized");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception in watch callback", e);
                    }
                });
    }

    public void listenUnder(GlobType type, Listener listener, FieldValues orderedPath) {
        Listener logListener = new LoggerListener(listener);
        GlobDeserializer.Deserializer globBinReader = deserializer.with(GlobTypeResolver.from(type));
        watchClient.watch(ByteSequence.from(extractPath(orderedPath, type), StandardCharsets.UTF_8), WatchOption.newBuilder()
                        .withPrevKV(true)
                        .isPrefix(true).build(),
                watchResponse -> {
                    try {
                        for (WatchEvent event : watchResponse.getEvents()) {
                            if (event.getEventType() == WatchEvent.EventType.DELETE) {
                                globBinReader.read(event.getPrevKV().getValue().getBytes())
                                        .ifPresent(logListener::delete);
                            } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                                globBinReader.read(event.getKeyValue().getValue().getBytes())
                                        .ifPresent(logListener::put);
                            } else {
                                LOGGER.info("event not unrecognized");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception in watch callback", e);
                    }
                });
    }

    public CompletableFuture<Void> delete(GlobType type, FieldValues values) {
        return kv.delete(ByteSequence.from(extractPath(values, type), StandardCharsets.UTF_8))
                .thenApply(deleteResponse -> null);
    }

    public void end() {
        client.close();
    }

    private static class LoggerListener implements Listener {
        private final Listener listener;

        public LoggerListener(Listener listener) {
            this.listener = listener;
        }

        public void put(Glob glob) {
            LOGGER.info("Receive put " + GSonUtils.encode(glob, true));
            try {
                listener.put(glob);
            } catch (Exception e) {
                LOGGER.error("Got exception", e);
            }
        }

        public void delete(Glob glob) {
            LOGGER.info("Receive delete " + GSonUtils.encode(glob, true));
            try {
                listener.delete(glob);
            } catch (Exception e) {
                LOGGER.error("Got exception", e);
            }
        }
    }
}
