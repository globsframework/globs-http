package org.globsframework.remote.shared.impl;

import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.model.utils.GlobMatchers;
import org.globsframework.remote.shared.AbstractSharedDataEventListener;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataService;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.Ref;
import org.globsframework.utils.TestUtils;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerSharedDataTest {

    static final int EVENTS_COUNT = 50000;
    static final int CLIENT_COUNT = 3;

    static {
    }

    @Test
    @Ignore
    public void testShare() throws Exception {
        GlobModel globModel = new DefaultGlobModel(SourceLocation.TYPE);
        ServerSharedData serverSharedData = new DefaultServerSharedData(globModel, "localhost", 0, "/");
        int port = serverSharedData.getPort();

        final SharedDataService dataService1 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
        dataService1.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.create(SourceLocation.TYPE,
                        FieldValue.value(SourceLocation.ID, 1),
                        FieldValue.value(SourceLocation.SHARED_ID, dataService1.getId()),
                        FieldValue.value(SourceLocation.SOURCE_NAME, "data"),
                        FieldValue.value(SourceLocation.URL, "here 1"));
            }
        });

        final SharedDataService dataService2 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
        dataService2.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.create(SourceLocation.TYPE,
                        FieldValue.value(SourceLocation.ID, 1),
                        FieldValue.value(SourceLocation.SHARED_ID, dataService2.getId()),
                        FieldValue.value(SourceLocation.SOURCE_NAME, "data"),
                        FieldValue.value(SourceLocation.URL, "here 2"));
            }
        });

        // data are shared.
        final GlobList all = new GlobList();

        final OnceSet<SharedDataService> futureDataService = new OnceSet<>();
        final SharedDataService dataService3 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/", new AbstractSharedDataEventListener() {
            public void reset() {
                futureDataService.get().read(new SharedDataService.SharedData() {
                    public void data(GlobRepository globRepository) {
                        all.clear();
                        all.addAll(globRepository.getAll(SourceLocation.TYPE));
                    }
                });
            }
        });
        futureDataService.set(dataService3);

        long l = System.currentTimeMillis() + 1000;
        while (all.size() != 2 && l > System.currentTimeMillis()) {
            Thread.sleep(100);
        }
        assertEquals(2, all.size());

        final GlobList sources = new GlobList();
        dataService1.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                sources.addAll(globRepository.getAll(SourceLocation.TYPE,
                        GlobMatchers.fieldEquals(SourceLocation.URL, "here 2")));

            }
        });

        assertEquals(1, sources.size());
        serverSharedData.stop();

        // check all data are removed.
        do {
            dataService1.read(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    sources.clear();
                    sources.addAll(globRepository.getAll(SourceLocation.TYPE,
                            GlobMatchers.fieldEquals(SourceLocation.URL, "here 2")));

                }
            });
            Thread.sleep(100);
        } while (!sources.isEmpty());

        serverSharedData = new DefaultServerSharedData(globModel, "localhost", port, "/");

        // check data are received at reconnection
        do {
            dataService1.read(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    sources.clear();
                    sources.addAll(globRepository.getAll(SourceLocation.TYPE,
                            GlobMatchers.fieldEquals(SourceLocation.URL, "here 2")));

                }
            });
            Thread.sleep(100);
        } while (sources.isEmpty());

        dataService2.stop();

        // check that a data from source 2 are removed when source 2 die
        do {
            dataService1.read(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    sources.clear();
                    sources.addAll(globRepository.getAll(SourceLocation.TYPE,
                            GlobMatchers.fieldEquals(SourceLocation.URL, "here 2")));

                }
            });
            Thread.sleep(100);
        } while (!sources.isEmpty());


        //check update one source 1 is visible on source 3
        final Ref<Integer> countRef = new Ref<Integer>(0);
        dataService3.listen(new AbstractSharedDataEventListener() {
            public void event(ChangeSet changeSet) {
                changeSet.safeVisit(new ChangeSetVisitor() {
                    public void visitCreation(Key key, FieldsValueScanner values) throws Exception {
                    }

                    public void visitUpdate(Key key, FieldsValueWithPreviousScanner values) throws Exception {
                        Object o = TestUtils.get(values, SourceLocation.COUNT);
                        if (o != null) {
                            countRef.set((Integer) o);
                        }
                    }

                    public void visitDeletion(Key key, FieldsValueScanner previousValues) throws Exception {
                    }
                });
            }

            public void reset() {
            }
        });

        dataService1.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                Key key = KeyBuilder.create(SourceLocation.TYPE).set(SourceLocation.SHARED_ID, dataService1.getId()).set(SourceLocation.ID, 1).get();
                globRepository.update(key, SourceLocation.COUNT, globRepository.get(key).get(SourceLocation.COUNT) + 1);
            }
        });

        while (countRef.get() != 1) {
            Thread.sleep(100);
        }
    }

    @Ignore
    @Test
    public void testPerfs() throws Exception {
        final GlobModel globModel = new DefaultGlobModel(SourceLocation.TYPE);
        ServerSharedData serverSharedData = new DefaultServerSharedData(globModel, "localhost", 0, "/");
        final int port = serverSharedData.getPort();

        final SharedDataService clientSharedData = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
        final CountSharedDataEventListener sharedDataEventListener = new CountSharedDataEventListener(clientSharedData);
        clientSharedData.listen(sharedDataEventListener);

        Barrier barrier = new Barrier();
        ClientPublisherThread[] clientPublisherThreads = new ClientPublisherThread[CLIENT_COUNT];
        for (int i = 0; i < clientPublisherThreads.length; i++) {
            clientPublisherThreads[i] = new ClientPublisherThread(globModel, port, barrier, i);
            clientPublisherThreads[i].start();
        }

        barrier.wait(CLIENT_COUNT);
        NanoChrono chrono = NanoChrono.start();
        barrier.go();

        synchronized (sharedDataEventListener) {
            while (sharedDataEventListener.count != CLIENT_COUNT) {
                sharedDataEventListener.wait();
            }
        }
        double elapsedTime = chrono.getElapsedTimeInMS();
        System.out.println("receive " + EVENTS_COUNT * CLIENT_COUNT + " msg in " + elapsedTime + "ms ; " + ((CLIENT_COUNT * EVENTS_COUNT / elapsedTime) * 1000.) + " m/s in " + sharedDataEventListener.evtReceived + " msg.");
        for (ClientPublisherThread clientPublisherThread : clientPublisherThreads) {
            clientPublisherThread.join();
        }
        clientSharedData.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                GlobList all = globRepository.getAll(SourceLocation.TYPE);
                if (all.size() != CLIENT_COUNT) {
                    fail("To many globs");
                }
            }
        });
        for (ClientPublisherThread clientPublisherThread : clientPublisherThreads) {
            clientPublisherThread.delete();
        }
        Thread.sleep(10);
        clientSharedData.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                assertEquals(0, globRepository.getAll(SourceLocation.TYPE).size());
            }
        });
    }

    @Ignore
    @Test
    public void testExchangeType() throws InterruptedException {
        GlobModel globModel = new DefaultGlobModel();
        ServerSharedData serverSharedData = new DefaultServerSharedData(globModel, "localhost", 0, "/");
        int port = serverSharedData.getPort();

        final SharedDataService dataService1 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
        dataService1.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.create(SourceLocation.TYPE,
                        FieldValue.value(SourceLocation.ID, 1),
                        FieldValue.value(SourceLocation.SHARED_ID, dataService1.getId()),
                        FieldValue.value(SourceLocation.SOURCE_NAME, "data"),
                        FieldValue.value(SourceLocation.URL, "here 1"));
            }
        });

        final SharedDataService dataService2 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
        dataService2.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.create(SourceLocation.TYPE,
                        FieldValue.value(SourceLocation.ID, 1),
                        FieldValue.value(SourceLocation.SHARED_ID, dataService2.getId()),
                        FieldValue.value(SourceLocation.SOURCE_NAME, "data"),
                        FieldValue.value(SourceLocation.URL, "here 2"));
            }
        });

        // data are shared.
        final GlobList all = new GlobList();
        final OnceSet<SharedDataService> futureDataService = new OnceSet<>();
        final SharedDataService dataService3 = new ClientSharedData(new DefaultGlobModel(SourceLocation.TYPE), AddressAccessor.FixAddressAccessor.create("localhost", port),
                ClientSharedData.OnStop.NULL, "/", new AbstractSharedDataEventListener() {
            public void reset() {
                futureDataService.get().read(new SharedDataService.SharedData() {
                    public void data(GlobRepository globRepository) {
                        all.clear();
                        all.addAll(globRepository.getAll(SourceLocation.TYPE));
                    }
                });
            }
        });
        futureDataService.set(dataService3);

        long l = System.currentTimeMillis() + 1000000;
        while (all.size() != 2 && l > System.currentTimeMillis()) {
            Thread.sleep(100);
        }
        assertEquals(2, all.size());
    }

    private static class OnceSet<T> {
        private T item;

        public T get() {
            synchronized (this) {
                if (item != null) {
                    return item;
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                }
                return item;
            }
        }

        public void set(T item) {
            synchronized (this) {
                this.item = item;
                notifyAll();
            }
        }

    }

    private static class CountSharedDataEventListener implements SharedDataService.SharedDataEventListener {
        private final SharedDataService clientSharedData;
        int count = 0;
        int evtReceived = 0;

        public CountSharedDataEventListener(SharedDataService clientSharedData) {
            this.clientSharedData = clientSharedData;
        }

        public void event(ChangeSet changeSet) {
            if (changeSet.isEmpty()) {
                synchronized (CountSharedDataEventListener.this) {
                    GlobList all =
                            clientSharedData.read(new SharedDataService.SharedData() {
                                GlobList all;

                                public void data(GlobRepository globRepository) {
                                    all = globRepository.getAll(SourceLocation.TYPE);
                                }
                            }).all;
                    count = 0;
                    for (Glob glob : all) {
                        if (glob.get(SourceLocation.COUNT) == EVENTS_COUNT) {
                            count++;
                        }
                    }
                    CountSharedDataEventListener.this.notify();
                }
            }

            changeSet.safeVisit(new ChangeSetVisitor() {
                public void visitCreation(Key key, FieldsValueScanner values) throws Exception {
                    hasEvent(key, values);
                }

                public void visitUpdate(Key key, FieldsValueWithPreviousScanner values) throws Exception {
                    hasEvent(key, values);
                }

                private void hasEvent(Key key, FieldsValueScanner values) {
                    if (key.getGlobType() == SourceLocation.TYPE) {
                        Object o = TestUtils.get(values, SourceLocation.COUNT);
                        if (o != null) {
                            evtReceived++;
                            int count = (int) o;
                            if (count == EVENTS_COUNT) {
                                synchronized (CountSharedDataEventListener.this) {
                                    CountSharedDataEventListener.this.count++;
                                    CountSharedDataEventListener.this.notify();
                                }
                            }
                        }
                    }
                }

                public void visitDeletion(Key key, FieldsValueScanner previousValues) throws Exception {
                }
            });
        }

        public void reset() {
        }
    }

    static class Barrier {
        boolean go = false;
        int waitingCount;

        public void waitToRun() {
            synchronized (this) {
                waitingCount++;
                notifyAll();
                while (!go) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        public void wait(int count) {
            synchronized (this) {
                while (waitingCount != count) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        public void go() {
            synchronized (this) {
                go = true;
                notifyAll();
            }
        }
    }

    private static class ClientPublisherThread extends Thread {
        private final GlobModel globModel;
        private final int port;
        private final int thId;
        SharedDataService dataService1;
        private Barrier barrier;

        public ClientPublisherThread(GlobModel globModel, int port, Barrier barrier, int thId) {
            super("client");
            this.globModel = globModel;
            this.port = port;
            this.barrier = barrier;
            this.thId = thId;
        }

        public void run() {
            barrier.waitToRun();
            dataService1 = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create("localhost", port), ClientSharedData.OnStop.NULL, "/");
            dataService1.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    globRepository.create(SourceLocation.TYPE,
                            FieldValue.value(SourceLocation.ID, 1),
                            FieldValue.value(SourceLocation.SHARED_ID, dataService1.getId()),
                            FieldValue.value(SourceLocation.SOURCE_NAME, "data"),
                            FieldValue.value(SourceLocation.URL, "here " + thId));
                }
            });
            for (int i = 0; i <= EVENTS_COUNT; i++) {
                final int finalI = i;
                dataService1.write(new SharedDataService.SharedData() {
                    public void data(GlobRepository globRepository) {
                        Key key = KeyBuilder.create(SourceLocation.TYPE).set(SourceLocation.SHARED_ID, dataService1.getId()).set(SourceLocation.ID, 1).get();
                        globRepository.update(key, FieldValue.value(SourceLocation.COUNT, finalI),
                                FieldValue.value(SourceLocation.SOURCE_NAME, new String(new char[100]) + finalI));
                    }
                });
            }
        }

        void delete() {
            dataService1.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    globRepository.delete(KeyBuilder.create(SourceLocation.SHARED_ID, dataService1.getId(), SourceLocation.ID, 1));
                }
            });
        }
    }
}
