package org.globsframework.remote.shared.impl;

import org.globsframework.directory.DefaultDirectory;
import org.globsframework.directory.Directory;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.remote.DirectoryProvider;
import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.remote.rpc.ExportMethod;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.remote.rpc.impl.DefaultRpcService;
import org.globsframework.remote.shared.*;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.TestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MultiRpcServiceTest {

    static {
//        Log4j.initLog();
        System.setProperty("mra.peer.buffer.size", "300");
    }

    interface TestRpcCall {

        @ExportMethod
        int callMe(int a);

    }


    public static class TestRpcCallImpl implements TestRpcCall {
        private SharedDataService sharedDataServiceSync;
        private String id;

        public TestRpcCallImpl(final SharedDataService sharedDataServiceSync, final String id) {
            this.sharedDataServiceSync = sharedDataServiceSync;
            this.id = id;
            sharedDataServiceSync.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    org.globsframework.model.Key key = createKey(sharedDataServiceSync);
                    globRepository.create(key, FieldValue.value(SharedDummyObject.VALUE, 0));
                }
            });
        }

        public org.globsframework.model.Key createKey(SharedDataService sharedDataServiceSync) {
            return KeyBuilder.create(SharedDummyObject.ID, id,
                    SharedDummyObject.SHARE_ID, sharedDataServiceSync.getId());
        }

        public int callMe(final int a) {
            sharedDataServiceSync.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    globRepository.update(createKey(sharedDataServiceSync),
                            FieldValue.value(SharedDummyObject.TIME_STAMP, System.nanoTime()),
                            FieldValue.value(SharedDummyObject.VALUE, a));
                }
            });
            return -a;
        }
    }

    final static int COUNT = 1000;

    static class SharedServer {
        public static void main(String[] args) throws InterruptedException {
            SharedDataManager sharedDataManager;
            final DefaultDirectory directory = new DefaultDirectory();
//            directory.add(PeerToPeer.class, new DirectPeerToPeer());
            ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
            sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(serverSharedData.getHost(), serverSharedData.getPort()));
            directory.add(SharedDataManager.class, sharedDataManager);
            DefaultRpcService.registerRpcNamingServiceHere(sharedDataManager);

            SharedDataManager.Path path = SharedPathBuilder.create("/test");
            DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject.TYPE);
            sharedDataManager.create(path, model);

            System.out.println("RpcServiceTest.main " + serverSharedData.getHost() + " " + serverSharedData.getPort());
            synchronized (directory) {
                directory.wait();
            }
        }
    }

    static class Server {
        public static void main(String[] args) throws InterruptedException {
            if (args.length == 0) {
                System.out.println("Server.main host port name1 name2 ...");
            } else {
                final DefaultDirectory directory = new DefaultDirectory();
                directory.add(PeerToPeer.class, new DirectPeerToPeer());
                SharedDataManager sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(args[0], Integer.parseInt(args[1])));
                SharedDataManager.Path path = SharedPathBuilder.create("/test");
                DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject.TYPE);

                SharedDataService sharedDataServiceSync = sharedDataManager.getSharedDataServiceSync(path, model);

                directory.add(SharedDataManager.class, sharedDataManager);
                RpcService rpcService = new DefaultRpcService("worker", new DirectoryProvider() {
                    public Directory getDirectory() {
                        return directory;
                    }
                }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
                for (int i = 2; i < args.length; i++) {
                    String s = args[i];
                    rpcService.register(TestRpcCall.class, new TestRpcCallImpl(sharedDataServiceSync, s), s);
                    System.out.println("RpcServiceTest.main export " + s);
                }
                synchronized (directory) {
                    directory.wait();
                }
            }
        }
    }

    static class Client {
        public static void main(String[] args) throws ExecutionException, InterruptedException {
            if (args.length == 0) {
                System.out.println("Client.main host port name1 name2 ...");
            } else {
                final DefaultDirectory directory = new DefaultDirectory();
                directory.add(PeerToPeer.class, new DirectPeerToPeer());
                SharedDataManager sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(args[0], Integer.parseInt(args[1])));

                SharedDataManager.Path path = SharedPathBuilder.create("/test");
                DefaultGlobModel model = new DefaultGlobModel(SharedDummyObject.TYPE);
                SharedDataService sharedDataServiceSync = sharedDataManager.getSharedDataServiceSync(path, model);


                directory.add(SharedDataManager.class, sharedDataManager);
                RpcService rpcService = new DefaultRpcService("worker", new DirectoryProvider() {
                    public Directory getDirectory() {
                        return directory;
                    }
                }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
                String clientName[] = new String[args.length - 2];
                for (int i = 2; i < args.length; i++) {
                    String arg = args[i];
                    clientName[i - 2] = arg;
                }
                final RpcCaller rpcCalls[] = new RpcCaller[clientName.length];
                for (int i = 0; i < rpcCalls.length; i++) {
                    rpcCalls[i] = new RpcCaller(clientName[i], rpcService.getService(TestRpcCall.class, clientName[i]));
                }
                Future[] futures = new Future[clientName.length];
                ExecutorService executorService = Executors.newFixedThreadPool(rpcCalls.length);
                loopCall(rpcCalls, futures, executorService, 200, sharedDataServiceSync);
                NanoChrono chrono = NanoChrono.start();
                loopCall(rpcCalls, futures, executorService, COUNT, sharedDataServiceSync);
                double elapsedTimeInMS = chrono.getElapsedTimeInMS();
                System.out.println("RpcServiceTest.testName " + ((double) COUNT) * 1000. / elapsedTimeInMS + " loop/s for " + rpcCalls.length + " call per loop " + elapsedTimeInMS / COUNT + "ms per loop");
                executorService.shutdown();
            }
        }

        static class RpcCaller {
            TestRpcCall testRpcCall;
            String name;
            public long timestamp;
            public long receivedAt;

            public RpcCaller(String name, TestRpcCall testRpcCall) {
                this.name = name;
                this.testRpcCall = testRpcCall;
            }

            synchronized public void waitIfNeeded() throws InterruptedException {
                while (receivedAt ==0){
                    wait();
                }
            }
        }

        public static void loopCall(RpcCaller[] rpcCalls, Future[] futures,
                                    ExecutorService executorService, int i2, SharedDataService sharedDataServiceSync) throws InterruptedException, ExecutionException {
            final Map<String, RpcCaller> rpcCallerLongHashMap = new HashMap<>();
            for (RpcCaller rpcCall : rpcCalls) {
                rpcCallerLongHashMap.put(rpcCall.name, rpcCall);
            }
            sharedDataServiceSync.listen(new SharedDataService.SharedDataEventListener() {
                public void event(ChangeSet changeSet) {
                    changeSet.safeVisit(new ChangeSetVisitor() {
                        public void visitCreation(org.globsframework.model.Key key, FieldsValueScanner values) throws Exception {

                        }

                        public void visitUpdate(org.globsframework.model.Key key, FieldsValueWithPreviousScanner values) throws Exception {
                            RpcCaller rpcCaller = rpcCallerLongHashMap.get(key.get(SharedDummyObject.ID));
                            rpcCaller.timestamp = (long) TestUtils.get(values, SharedDummyObject.TIME_STAMP);
                            rpcCaller.receivedAt = System.nanoTime();
                            synchronized (rpcCaller){
                                rpcCaller.notify();
                            }
                        }

                        public void visitDeletion(org.globsframework.model.Key key, FieldsValueScanner previousValues) throws Exception {

                        }
                    });
                }

                public void reset() {

                }
            });
            for (int i = 0; i < i2; i++) {
                long startAt = System.nanoTime();
                for (int i1 = 0; i1 < rpcCalls.length; i1++) {
//                        rpcCalls[i1].callMe(i);
                    RpcRunnableCall task = new RpcRunnableCall(i, rpcCalls, i1);
//                        task.run();
                    futures[i1] = executorService.submit(task);
                }
                for (Future future : futures) {
                    future.get();
                }
                long completeAt = System.nanoTime();
                for (RpcCaller rpcCall : rpcCalls) {
                    rpcCall.waitIfNeeded();
                }
                long propagateDone = System.nanoTime();
                long l = propagateDone - completeAt;
                if (l > 1000000) {
                    System.out.println("Client.loopCall take " + + (l / 1000000) + "ms");
                }
                for (RpcCaller rpcCall : rpcCalls) {
                    long l1 = rpcCall.timestamp - startAt;
                    if (l1 > 1000000) {
                        System.out.println("Client.loopCall " + (l1 / 1000000) + "ms");
                    }
                }
            }
        }
    }

    private static class RpcRunnableCall implements Runnable {
        private final int i;
        private final Client.RpcCaller[] rpcCalls;
        private final int i1;

        public RpcRunnableCall(int i, Client.RpcCaller[] rpcCalls, int i1) {
            this.i = i;
            this.rpcCalls = rpcCalls;
            this.i1 = i1;
        }

        public void run() {
            if (-i != rpcCalls[i1].testRpcCall.callMe(i)) {
                throw new RuntimeException("Call me fail");
            }
        }
    }


    public static class SharedDummyObject {
        public static GlobType TYPE;

        @SharedId_
        @KeyField
        public static IntegerField SHARE_ID;

        @KeyField
        public static StringField ID;

        public static LongField TIME_STAMP;

        public static IntegerField VALUE;

        static {
            GlobTypeLoaderFactory.create(SharedDummyObject.class).load();
        }
    }

}
