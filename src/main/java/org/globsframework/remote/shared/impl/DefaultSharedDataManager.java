package org.globsframework.remote.shared.impl;

import org.globsframework.directory.Cleanable;
import org.globsframework.directory.Directory;
import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.annotations.AllAnnotations;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.model.utils.GlobMatcher;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataManager;
import org.globsframework.remote.shared.SharedDataService;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.collections.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class DefaultSharedDataManager implements SharedDataManager, SharedDataService.SharedDataEventListener, Cleanable {
    public static final GlobModel GLOB_MODEL = new DefaultGlobModel(AllAnnotations.MODEL, ShareDataManagerType.TYPE);
    static private final Logger LOGGER = LoggerFactory.getLogger(DefaultSharedDataManager.class);
    private static final int INITIALIZATION_TIMEOUT = Integer.getInteger("org.globsframework.remote.shared.impl.DefaultSharedDataManager.initialization.timeout", 60 * 1000);
    private final String localHost;
    private final SharedDataService sharedDataService;
    private final Map<Path, ServerSharedData> serverData = new HashMap<>();
    private final Map<Path, SharedDataService> sharedDataServices = new HashMap<>();

    private DefaultSharedDataManager(String localHost, SharedDataService sharedDataService) {
        this.localHost = localHost;
        this.sharedDataService = sharedDataService;
        sharedDataService.listen(this);
        sharedDataService.waitForInitialization(10000);
    }

    static public ServerSharedData initSharedData(String host, int port) {
        return new DefaultServerSharedData(GLOB_MODEL, host, port, "/");
    }

    static public ServerSharedData initSharedData() {
        return new DefaultServerSharedData(GLOB_MODEL, "/");
    }

    static public SharedDataManager create(AddressAccessor addressAccessor) {
        return new DefaultSharedDataManager(null, new ClientSharedData(GLOB_MODEL, addressAccessor, ClientSharedData.OnStop.NULL, "root"));
    }

    static public SharedDataManager create(AddressAccessor addressAccessor, String localHost) {
        return new DefaultSharedDataManager(localHost, new ClientSharedData(GLOB_MODEL, addressAccessor, ClientSharedData.OnStop.NULL, "root"));
    }

    synchronized public void close() {
        sharedDataService.remove(this);
        sharedDataService.stop();
        for (SharedDataService dataService : sharedDataServices.values()) {
            dataService.stop();
        }
        for (ServerSharedData serverSharedData : serverData.values()) {
            serverSharedData.stop();
        }
        sharedDataServices.clear();
        serverData.clear();
    }

    public void create(final Path path, GlobModel model) throws AlreadyExist {
        create(path, model, 0);
    }

    synchronized public void create(final Path path, GlobModel model, int port) throws AlreadyExist {
        ServerSharedData serverSharedData = serverData.get(path);
        if (serverSharedData != null) {
            throw new AlreadyExist(path.toString());
        }
        LOGGER.info("create server shared data for " + path.getFullPath());
        final ServerSharedData sharedData = new DefaultServerSharedData(model, localHost, port, path.getFullPath());
        serverData.put(path, sharedData);
        sharedDataService.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.create(ShareDataManagerType.TYPE, FieldValue.value(ShareDataManagerType.SHARED_ID, sharedDataService.getId()),
                        FieldValue.value(ShareDataManagerType.PATH, path.getFullPath()),
                        FieldValue.value(ShareDataManagerType.PATH_ELEMENT_COUNT, path.getElementCount()),
                        FieldValue.value(ShareDataManagerType.HOST, sharedData.getHost()),
                        FieldValue.value(ShareDataManagerType.PORT, sharedData.getPort()));
            }
        });
    }

    synchronized public SharedDataService getSharedDataService(final Path path, GlobModel model) {
        SharedDataService dataService = sharedDataServices.get(path);
        if (dataService != null) {
            return dataService;
        }

        dataService = new ClientSharedData(model, new AddressAccessor() {
            public Pair<String, Integer> getHostAndPort() {
                return getServiceUrl(path, 10000);
            }
        }, new ClientSharedData.OnStop() {
            public void stopped() {
                synchronized (DefaultSharedDataManager.this) {
                    sharedDataServices.remove(path);
                }
            }
        }, path.getFullPath());
        sharedDataServices.put(path, dataService);
        return dataService;
    }

    synchronized public SharedDataService getSharedDataServiceSync(final Path path, GlobModel model) {
        SharedDataService sharedDataService = getSharedDataService(path, model);
        if (!sharedDataService.waitForInitialization(INITIALIZATION_TIMEOUT)) {
            throw new RuntimeException("SharedDataService not initialized!");
        }
        return sharedDataService;
    }

    private Pair<String, Integer> getServiceUrl(final Path path, int timeout) {
        NanoChrono chrono = NanoChrono.start();
        Pair<String, Integer> url = null;
        while (url == null && (timeout > 0 && chrono.getElapsedTimeInMS() < timeout)) {
            url = sharedDataService.read(new SharedDataService.SharedData() {
                Pair<String, Integer> url1 = null;

                public void data(GlobRepository globRepository) {
                    GlobList globs = globRepository.findByIndex(ShareDataManagerType.PATH_INDEX, path.getFullPath());
                    if (!globs.isEmpty()) {
                        Glob glob = globs.get(0);
                        url1 = Pair.makePair(glob.get(ShareDataManagerType.HOST), glob.get(ShareDataManagerType.PORT));
                    }
                }
            }).url1;
            if (url == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        LOGGER.info("Shared data " + path + (url != null ? "" : " not ") + " available after " + chrono.getElapsedTimeInMS() + " ms.");
        return url;
    }

    public Path[] listService(final Path parent) {
        return sharedDataService.read(new SharedDataService.SharedData() {
            Path[] paths = new Path[0];

            public void data(GlobRepository globRepository) {
                GlobList all = globRepository.getAll(ShareDataManagerType.TYPE, new GlobMatcher() {
                    public boolean matches(Glob item, GlobRepository repository) {
                        return item.get(ShareDataManagerType.PATH).startsWith(parent.getFullPath()) &&
                                item.get(ShareDataManagerType.PATH_ELEMENT_COUNT) == parent.getElementCount() + 1;
                    }
                });
                paths = new Path[all.size()];
                int i = 0;
                for (Glob glob : all) {
                    paths[i] = new SharedPathImpl(glob.get(ShareDataManagerType.PATH));
                    i++;
                }
            }
        }).paths;
    }

    public void event(final ChangeSet changeSet) {
        sharedDataService.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                Set<Key> deleted = changeSet.getDeleted(ShareDataManagerType.TYPE);
                for (Key key : deleted) {
                    sharedDataServices.remove(SharedPathBuilder.create(globRepository.get(key).get(ShareDataManagerType.PATH)));
                }
            }
        });
    }

    public void reset() {
        sharedDataService.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                Set<String> all = globRepository.getAll(ShareDataManagerType.TYPE).getValueSet(ShareDataManagerType.PATH);
                for (Iterator<Path> iterator = sharedDataServices.keySet().iterator(); iterator.hasNext(); ) {
                    Path path = iterator.next();
                    if (!all.contains(path.getFullPath())) {
                        iterator.remove();
                    }
                }
            }
        });
    }

    public void clean(Directory directory) {
        close();
    }
}
