package org.globsframework.remote.shared.impl;

import junit.framework.TestCase;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.AutoIncrement;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.model.delta.DefaultChangeSet;
import org.globsframework.model.delta.MutableChangeSet;
import org.globsframework.model.format.GlobPrinter;
import org.globsframework.model.utils.GlobMatcher;
import org.globsframework.model.utils.GlobMatchers;
import org.globsframework.remote.shared.*;
import org.globsframework.utils.collections.Pair;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DefaultSharedDataManagerTest {

    public static final int TIMEOUT = 150000;

    static {
//        Log4j.initLog();
    }


    @Ignore
    @Test
    public void testStopClient() throws Exception {
        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
        SharedDataManager centralSharedDataManager = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataManager.Path path = SharedPathBuilder.create("/test");
        DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject1.TYPE);
        centralSharedDataManager.create(path, model);


        SharedDataManager sharedDataManager1 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataManager sharedDataManager2 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));


        SharedDataService sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        final SharedDataService sharedDataService2 = sharedDataManager2.getSharedDataService(path, model);

        SharedDataServiceChecker sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        SharedDataServiceChecker sharedDataServiceChecker2 = new SharedDataServiceChecker("data 2", sharedDataService2);

        sharedDataServiceChecker1.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l1"));
        sharedDataServiceChecker2.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l2"));

        GlobMatcher l1 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l1");
        GlobMatcher l2 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l2");
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);

        sharedDataService1.stop();

        sharedDataServiceChecker2.checkNotContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l2);

        sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        sharedDataServiceChecker1.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l1"));
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);
    }

    @Ignore
    @Test
    public void testStopStartServer() throws Exception {
        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
        SharedDataManager centralSharedDataManager = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataManager.Path path = SharedPathBuilder.create("/test");
        DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject1.TYPE);
        centralSharedDataManager.create(path, model);


        SharedDataManager sharedDataManager1 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataManager sharedDataManager2 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));


        SharedDataService sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        final SharedDataService sharedDataService2 = sharedDataManager2.getSharedDataService(path, model);

        SharedDataServiceChecker sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        SharedDataServiceChecker sharedDataServiceChecker2 = new SharedDataServiceChecker("data 2", sharedDataService2);

        sharedDataServiceChecker1.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l1"));
        sharedDataServiceChecker2.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l2"));

        GlobMatcher l1 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l1");
        GlobMatcher l2 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l2");
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);

        centralSharedDataManager.close();

        sharedDataServiceChecker2.checkNotContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l2);

        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkNotContain(SharedDummyObject1.TYPE, l2);

        //restart

        centralSharedDataManager = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        centralSharedDataManager.create(path, model);

        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l2);

        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l1);

    }


    static class Server implements AddressAccessor {


        private ServerSharedData serverSharedData;

        void newServer(){
            serverSharedData = DefaultSharedDataManager.initSharedData();
        }
        public Pair<String, Integer> getHostAndPort() {
            return Pair.makePair(serverSharedData.getHost(), serverSharedData.getPort());
        }

        public void stop() {
            serverSharedData.stop();
        }
    }

    @Ignore
    @Test
    public void testStopAllExpectOne() throws Exception {
        Server server = new Server();
        server.newServer();
        SharedDataManager centralSharedDataManager = DefaultSharedDataManager.create(server);
        SharedDataManager.Path path = SharedPathBuilder.create("/test");
        DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject1.TYPE);
        centralSharedDataManager.create(path, model);


        SharedDataManager sharedDataManager1 = DefaultSharedDataManager.create(server);
        SharedDataManager sharedDataManager2 = DefaultSharedDataManager.create(server);


        SharedDataService sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        SharedDataService sharedDataService2 = sharedDataManager2.getSharedDataService(path, model);

        SharedDataServiceChecker sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        SharedDataServiceChecker sharedDataServiceChecker2 = new SharedDataServiceChecker("data 2", sharedDataService2);

        sharedDataServiceChecker1.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l1"));
        sharedDataServiceChecker2.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l2"));

        GlobMatcher l1 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l1");
        GlobMatcher l2 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l2");
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);

        System.out.println("DefaultSharedDataManagerTest.testStopAllExpectOne 1 : " + sharedDataService1.getId());
        System.out.println("DefaultSharedDataManagerTest.testStopAllExpectOne 2 : " + sharedDataService2.getId());
        sharedDataManager1.close();
        server.stop();

        Thread.sleep(2000);

        server.newServer();
        Thread.sleep(2000);
        System.out.println("DefaultSharedDataManagerTest.testStopAllExpectOne " + sharedDataService2.getId());
        sharedDataManager1 = DefaultSharedDataManager.create(server);
        sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);

    }

    @Ignore
    @Test
    public void testStartClientFirstAtGivenPort() throws InterruptedException {
        AddressAccessor.FixAddressAccessor addressAccessor = new AddressAccessor.FixAddressAccessor("localhost", 3000);
        SharedDataManager.Path path = SharedPathBuilder.create("/test");
        DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject1.TYPE);


        SharedDataManager sharedDataManager1 = DefaultSharedDataManager.create(addressAccessor);
        SharedDataManager sharedDataManager2 = DefaultSharedDataManager.create(addressAccessor);


        SharedDataService sharedDataService1 = sharedDataManager1.getSharedDataService(path, model);
        SharedDataService sharedDataService2 = sharedDataManager2.getSharedDataService(path, model);

        SharedDataServiceChecker sharedDataServiceChecker1 = new SharedDataServiceChecker("data 1", sharedDataService1);
        SharedDataServiceChecker sharedDataServiceChecker2 = new SharedDataServiceChecker("data 2", sharedDataService2);

        Thread.sleep(1000);
        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData("localhost", 3000);
        SharedDataManager centralSharedDataManager = DefaultSharedDataManager.create(addressAccessor);
        centralSharedDataManager.create(path, model);

        sharedDataServiceChecker1.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l1"));
        sharedDataServiceChecker2.create(SharedDummyObject1.TYPE, FieldValue.value(SharedDummyObject1.ID, 1), FieldValue.value(SharedDummyObject1.LABEL1, "l2"));

        GlobMatcher l1 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l1");
        GlobMatcher l2 = GlobMatchers.fieldEquals(SharedDummyObject1.LABEL1, "l2");
        sharedDataServiceChecker2.checkContain(SharedDummyObject1.TYPE, l1);
        sharedDataServiceChecker1.checkContain(SharedDummyObject1.TYPE, l2);

        sharedDataManager1.close();
        sharedDataManager2.close();
        serverSharedData.stop();
    }

    public static class SharedDummyObject1 {
        public static GlobType TYPE;

        @SharedId_
        @KeyField
        public static IntegerField SHARE_ID;

        @KeyField
        @AutoIncrement
        public static IntegerField ID;

        public static StringField LABEL1;

        public static StringField LABEL2;

        static {
            GlobTypeLoaderFactory.create(SharedDummyObject1.class).load();
        }
    }


    static class SharedDataServiceChecker implements SharedDataService.SharedDataEventListener {
        private final String name;
        final SharedDataService sharedDataService;
        MutableChangeSet current = new DefaultChangeSet();
        int count = 0;

        SharedDataServiceChecker(String name, SharedDataService sharedDataService) {
            this.name = name;
            this.sharedDataService = sharedDataService;
            sharedDataService.listen(this);
        }

        public void event(ChangeSet changeSet) {
            current.merge(changeSet);
            synchronized (this) {
                count++;
                notifyAll();
            }
        }

        public void reset() {
            sharedDataService.read(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    current.clear(globRepository.getTypes());
                    synchronized (SharedDataServiceChecker.this) {
                        count++;
                        SharedDataServiceChecker.this.notifyAll();
                    }
                }
            });
        }

        public void checkContain(GlobType type, final GlobMatcher globMatcher) throws InterruptedException {
            if (get(type, globMatcher) == null) {
                long timeout = System.currentTimeMillis() + TIMEOUT;
                while (get(type, globMatcher) == null && timeout > System.currentTimeMillis()) {
                    synchronized (this) {
                        this.wait(200);
                    }
                }
            }
            assertNotNull(new TraceRepo(sharedDataService).toString() + " => " + globMatcher, get(type, globMatcher));
        }

        private Glob get(final GlobType type, final GlobMatcher globMatcher) {
            return sharedDataService.read(new SharedDataService.SharedData() {
                Glob glob;

                public void data(GlobRepository globRepository) {
                    glob = globRepository.getAll(type, globMatcher).getFirst().orElse(null);
                }
            }).glob;
        }

        public void checkNotContain(GlobType type, final GlobMatcher globMatcher) throws InterruptedException {
            if (get(type, globMatcher) != null) {
                long timeout = System.currentTimeMillis() + TIMEOUT;
                while (get(type, globMatcher) != null && timeout > System.currentTimeMillis()) {
                    synchronized (this) {
                        this.wait(200);
                    }
                }
            }
            assertNull(new TraceRepo(sharedDataService).toString() + " => " + globMatcher, get(type, globMatcher));
        }

        public void create(final GlobType type, final FieldValue... values) {
            sharedDataService.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    MutableGlob instantiate = type.instantiate();
                    for (FieldValue value : values) {
                        instantiate.setValue(value.getField(), value.getValue());
                    }
                    for (Field field : type.getFields()) {
                        if (field.hasAnnotation(SharedId.KEY)) {
                            instantiate.setValue(field, sharedDataService.getId());
                        }
                    }
                    globRepository.create(instantiate);
                }
            });
        }

        public org.globsframework.model.Key createKey(final KeyBuilder keyBuilder) {
            return sharedDataService.read(new SharedDataService.SharedData() {
                Key key;

                public void data(GlobRepository globRepository) {
                    Field[] fields = keyBuilder.getGlobType().getFields();
                    for (Field field : fields) {
                        if (field.hasAnnotation(SharedId.KEY)) {
                            keyBuilder.setValue(field, sharedDataService.getId());
                        }
                    }
                    key = keyBuilder.get();
                }
            }).key;
        }
    }

    public static class TraceRepo {
        final SharedDataService sharedDataService;

        public TraceRepo(SharedDataService sharedDataService) {
            this.sharedDataService = sharedDataService;
        }


        public String toString() {
            return sharedDataService.read(new SharedDataService.SharedData() {
                String str;

                public void data(GlobRepository globRepository) {
                    str = GlobPrinter.init(globRepository).toString();
                }
            }).str;
        }
    }
}
