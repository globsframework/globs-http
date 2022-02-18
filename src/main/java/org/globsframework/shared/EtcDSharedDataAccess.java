package org.globsframework.shared;

import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
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
    private final BinWriterFactory binWriterFactory;
    private final BinReaderFactory binReaderFactory;
    private final Watch watchClient;
    private final KV kv;
    private final Lease leaseClient;

    public EtcDSharedDataAccess(Client client) {
        this.client = client;
        kv = client.getKVClient();
        watchClient = client.getWatchClient();
        leaseClient = client.getLeaseClient();
        binWriterFactory = BinWriterFactory.create();
        binReaderFactory = BinReaderFactory.create();
    }

    public CompletableFuture<Void> register(Glob glob) {
        GlobType type = glob.getType();
        String path = extractPath(glob, type);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        binWriterFactory.create(outputStream).write(glob);
        CompletableFuture<PutResponse> put = kv.put(ByteSequence.from(path, StandardCharsets.UTF_8), ByteSequence.from(outputStream.toByteArray()));
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
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        binWriterFactory.create(outputStream).write(glob);

        ByteSequence k = ByteSequence.from(path, StandardCharsets.UTF_8);
        ByteSequence v = ByteSequence.from(outputStream.toByteArray());

        return leaseClient.grant(unit.toSeconds(timeOut))
                .thenApply(LeaseGrantResponse::getID)
                .thenCompose(leaseId ->
                        kv.put(k, v, PutOption.newBuilder().withLeaseId(leaseId).build())
                        .thenApply(putResponse -> () -> leaseClient.keepAliveOnce(leaseId)));
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
            GlobBinReader globBinReader = binReaderFactory.createGlobBinReader(GlobTypeResolver.from(type));
            return globBinReader.read(new ByteArrayInputStream(value.getBytes()));
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
                GlobBinReader globBinReader = binReaderFactory.createGlobBinReader(GlobTypeResolver.from(type));
                globBinReader.read(new ByteArrayInputStream(value.getBytes()))
                        .ifPresent(data::add);
            }
            return data;
        });
    }

    public void listen(GlobType type, Listener listener, FieldValues orderedPath) {
        GlobBinReader globBinReader = binReaderFactory.createGlobBinReader(GlobTypeResolver.from(type));
        watchClient.watch(ByteSequence.from(extractPath(orderedPath, type), StandardCharsets.UTF_8),
                watchResponse -> {
                    for (WatchEvent event : watchResponse.getEvents()) {
                        if (event.getEventType() == WatchEvent.EventType.DELETE) {
                            globBinReader.read(new ByteArrayInputStream(event.getPrevKV().getValue().getBytes()))
                                    .ifPresent(listener::delete);
                        } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                            globBinReader.read(new ByteArrayInputStream(event.getKeyValue().getValue().getBytes()))
                                    .ifPresent(listener::put);
                        }
                    }
                });
    }

    public void listenUnder(GlobType type, Listener listener, FieldValues orderedPath) {
        GlobBinReader globBinReader = binReaderFactory.createGlobBinReader(GlobTypeResolver.from(type));
        watchClient.watch(ByteSequence.from(extractPath(orderedPath, type), StandardCharsets.UTF_8), WatchOption.newBuilder().isPrefix(true).build(),
                watchResponse -> {
                    for (WatchEvent event : watchResponse.getEvents()) {
                        if (event.getEventType() == WatchEvent.EventType.DELETE) {
                            globBinReader.read(new ByteArrayInputStream(event.getPrevKV().getValue().getBytes()))
                                    .ifPresent(listener::delete);
                        } else if (event.getEventType() == WatchEvent.EventType.PUT) {
                            globBinReader.read(new ByteArrayInputStream(event.getKeyValue().getValue().getBytes()))
                                    .ifPresent(listener::put);
                        }
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
}
