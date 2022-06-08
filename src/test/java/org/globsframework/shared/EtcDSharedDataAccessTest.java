package org.globsframework.shared;

import com.google.protobuf.ByteString;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.options.DeleteOption;
import junit.framework.Assert;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.FieldValues;
import org.globsframework.model.FieldValuesBuilder;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.serialisation.model.FieldNumber_;
import org.globsframework.shared.model.PathIndex_;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EtcDSharedDataAccessTest {
    public static final String[] ETCD = new String[]{"http://localhost:2379"}; //, "http://localhost:4001"

    @Before
    public void setUp() throws Exception {
        Client client = Client.builder().endpoints(ETCD).build();
        deleteAll(client);
        client.close();
    }

    @After
    public void tearDown() throws Exception {
        Client client = Client.builder().endpoints(ETCD).build();
        deleteAll(client);
        client.close();
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void tryLockGetInListener() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);

        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");


        CompletableFuture<Boolean> done = new CompletableFuture<>();
        etcDSharedDataAccess.listen(data.getType(), new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                try {
                    etcDSharedDataAccess.get(data.getType(), data).join();
                    done.complete(true);
                } catch (Exception e) {
                    done.complete(false);
                }
            }

            public void delete(Glob glob) {

            }
        }, data);

        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);

        Assert.assertTrue(done.join());
        etcDSharedDataAccess.end();
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void throwAErrorExceptionIn() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);

        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);
        BlockingQueue<Glob> added = new LinkedBlockingDeque<>();
        try {
            SharedDataAccess.ListenerCtrl listenerCtrl1 = etcDSharedDataAccess.getAndListenUnder(Data1.TYPE, FieldValues.EMPTY, new Consumer<List<Glob>>() {
                public void accept(List<Glob> globs) {
                    throw new RuntimeException("violent error");
                }
            }, new SharedDataAccess.Listener() {
                public void put(Glob glob) {
                    added.add(glob);
                }

                public void delete(Glob glob) {
                }
            }).join();
        } catch (CompletionException e) {
            Assert.assertEquals("java.lang.RuntimeException: violent error", e.getMessage());
        }


        Optional<Glob> glob = etcDSharedDataAccess.get(Data1.TYPE, data)
                .get(1, TimeUnit.MINUTES);
        Assert.assertTrue(glob.isPresent());
        MutableGlob data2 = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w2");

        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);
        Glob re = added.poll(4, TimeUnit.SECONDS);
        Assert.assertNotNull(re);
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void getUnderAndListenWithEmpty() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);

        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);
        etcDSharedDataAccess.delete(Data1.TYPE, data);

        BlockingQueue<Glob> puts = new ArrayBlockingQueue<>(10);
        BlockingQueue<Glob> deletes = new ArrayBlockingQueue<>(10);
        CompletableFuture<List<Glob>> res = new CompletableFuture<>();
        SharedDataAccess.ListenerCtrl listenerCtrl = etcDSharedDataAccess.getAndListenUnder(Data1.TYPE, FieldValues.EMPTY, new Consumer<List<Glob>>() {
            public void accept(List<Glob> globs) {
                etcDSharedDataAccess.register(data.duplicate().set(Data1.num, 3));
                res.complete(globs);
            }
        }, new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                puts.add(glob);
            }

            public void delete(Glob glob) {
                deletes.add(glob);
            }
        }).get();

        List<Glob> globs = res.get(10, TimeUnit.SECONDS);
        Assert.assertEquals(0, deletes.size());
        Assert.assertEquals(0, globs.size());
//        Assert.assertEquals(1, globs.get(0).get(Data1.num).intValue());

        data.set(Data1.num, 2);
        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);

        Glob poll = puts.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(poll);
        Assert.assertEquals(3, poll.get(Data1.num).intValue());
        Glob poll1 = puts.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(poll1);
        Assert.assertEquals(2, poll1.get(Data1.num).intValue());
        listenerCtrl.close();
        etcDSharedDataAccess.end();
//        deleteAll(client);
//        client.close();
    }


    @Test
    @Ignore("integration test to be filtered later")
    public void getUnderAndListen() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);

        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);

        BlockingQueue<Glob> puts = new ArrayBlockingQueue<>(10);
        BlockingQueue<Glob> deletes = new ArrayBlockingQueue<>(10);
        CompletableFuture<List<Glob>> res = new CompletableFuture<>();
        SharedDataAccess.ListenerCtrl listenerCtrl = etcDSharedDataAccess.getAndListenUnder(Data1.TYPE, FieldValues.EMPTY, new Consumer<List<Glob>>() {
            public void accept(List<Glob> globs) {
                try {
                    etcDSharedDataAccess.register(data.duplicate().set(Data1.num, 3)).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                res.complete(globs);
            }
        }, new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                puts.add(glob);
            }

            public void delete(Glob glob) {
                deletes.add(glob);
            }
        }).get();

        List<Glob> globs = res.get(10, TimeUnit.SECONDS);
        Assert.assertEquals(1, globs.size());
        Assert.assertEquals(1, globs.get(0).get(Data1.num).intValue());

        data.set(Data1.num, 2);
        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);

        Glob poll = puts.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(poll);
        Assert.assertEquals(3, poll.get(Data1.num).intValue());
        Glob poll1 = puts.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(poll1);
        Assert.assertEquals(2, poll1.get(Data1.num).intValue());
        listenerCtrl.close();
        deleteAll(client);
        etcDSharedDataAccess.end();
    }

    private void deleteAll(Client client) throws InterruptedException, ExecutionException {
        KV kvClient = client.getKVClient();
        kvClient.delete(ByteSequence.from(ByteString.copyFromUtf8("/")), DeleteOption.newBuilder()
                .isPrefix(true).build()).get();
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void testNameBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client clientDelete = Client.builder().endpoints(ETCD).build();
        Client client = Client.builder().endpoints(ETCD).build();
        Client clientRead = Client.builder().endpoints(ETCD).build();

        checkPutGet(EtcDSharedDataAccess.createBin(client), EtcDSharedDataAccess.createBin(clientRead));
        deleteAll(clientDelete);
        clientDelete.close();
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void testNameJson() throws ExecutionException, InterruptedException, TimeoutException {
        Client clientDelete = Client.builder().endpoints(ETCD).build();
        Client client = Client.builder().endpoints(ETCD).build();
        Client clientRead = Client.builder().endpoints(ETCD).build();
        checkPutGet(EtcDSharedDataAccess.createJson(client), EtcDSharedDataAccess.createJson(clientRead));
        deleteAll(clientDelete);
        clientDelete.close();
    }

    private void checkPutGet(SharedDataAccess etcDSharedDataAccess, SharedDataAccess sharedDataAccessRead) throws InterruptedException, ExecutionException, TimeoutException {
        SharedDataAccess.ListenerCtrl listenerCtrl = null;
        SharedDataAccess.ListenerCtrl listenerCtrl2 = null;
        SharedDataAccess.ListenerCtrl listenerCtrl1 = null;

        try {
            MutableGlob data = Data1.TYPE.instantiate()
                    .set(Data1.shop, "mg.the-oz.com")
                    .set(Data1.workerName, "w1")
                    .set(Data1.num, 1)
                    .set(Data1.someData, "blabla");

            BlockingQueue<Glob> puts = new ArrayBlockingQueue<>(10);
            BlockingQueue<Glob> deletes = new ArrayBlockingQueue<>(10);
            listenerCtrl = sharedDataAccessRead.listenUnder(Data1.TYPE, new SharedDataAccess.Listener() {
                public void put(Glob glob) {
                    System.out.println("EtcDSharedDataAccessTest.put ");
                    puts.add(glob);
                }

                public void delete(Glob glob) {
                    deletes.add(glob);
                }
            });

            BlockingQueue<Glob> puts2 = new ArrayBlockingQueue<>(10);
            BlockingQueue<Glob> deletes2 = new ArrayBlockingQueue<>(10);
            listenerCtrl2 = sharedDataAccessRead.listenUnder(Data2.TYPE, new SharedDataAccess.Listener() {
                public void put(Glob glob) {
                    puts2.add(glob);
                }

                public void delete(Glob glob) {
                    deletes2.add(glob);
                }
            });


            etcDSharedDataAccess.register(data)
                    .get(1, TimeUnit.MINUTES);

            etcDSharedDataAccess.register(Data2.TYPE.instantiate())
                    .get(1, TimeUnit.MINUTES);

            Assert.assertEquals("blabla", etcDSharedDataAccess.get(Data1.TYPE, FieldValuesBuilder.init()
                            .set(Data1.shop, "mg.the-oz.com")
                            .set(Data1.workerName, "w1")
                            .set(Data1.num, 1)
                            .get())
                    .get(10, TimeUnit.SECONDS)
                    .orElseThrow().get(Data1.someData));

            Assert.assertNotNull(puts.poll(10, TimeUnit.SECONDS));

            Assert.assertNotNull(puts2.poll(10, TimeUnit.SECONDS));

            data.set(Data1.num, 2);
            listenerCtrl1 = sharedDataAccessRead.listen(Data1.TYPE, new SharedDataAccess.Listener() {
                public void put(Glob glob) {
                    puts.add(glob);
                }

                public void delete(Glob glob) {
                    deletes.add(glob);
                }
            }, data);

            etcDSharedDataAccess.registerWithLease(data, 1, TimeUnit.MINUTES);

            Assert.assertNotNull(puts.poll(10, TimeUnit.SECONDS));

            List<Glob> actual = etcDSharedDataAccess.getUnder(Data1.TYPE, FieldValuesBuilder.init()
                            .set(Data1.shop, "mg.the-oz.com")
                            .set(Data1.workerName, "w1")
                            .get())
                    .get(10, TimeUnit.SECONDS);
            Assert.assertEquals(2, actual.size());

            etcDSharedDataAccess.delete(Data1.TYPE, actual.get(0));
            etcDSharedDataAccess.delete(Data1.TYPE, actual.get(1));

            Assert.assertNotNull(deletes.poll(10, TimeUnit.SECONDS));
        } finally {
            if (listenerCtrl != null) {
                listenerCtrl.close();
            }
            if (listenerCtrl1 != null) {
                listenerCtrl1.close();
            }
            if (listenerCtrl2 != null) {
                listenerCtrl2.close();
            }
            etcDSharedDataAccess.end();
            sharedDataAccessRead.end();
        }

    }


    @Test
    @Ignore("integration test to be filtered later")
    public void testLeaseBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        try {
            SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);
            checkLease(etcDSharedDataAccess);
            etcDSharedDataAccess.end();
        } finally {
            Client clientDelete = Client.builder().endpoints(ETCD).build();
            deleteAll(clientDelete);
            clientDelete.close();
        }
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void testLeaseJson() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        try {
            SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createJson(client);
            checkLease(etcDSharedDataAccess);
        } finally {
            Client clientDelete = Client.builder().endpoints(ETCD).build();
            deleteAll(clientDelete);
            clientDelete.close();
        }
    }

    @Test
    @Ignore
    public void autoLease() throws ExecutionException, InterruptedException {
        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");
        Client client = Client.builder().endpoints(ETCD).build();
        try {
            SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createJson(client);
            SharedDataAccess.UnLeaser unLeaser = etcDSharedDataAccess.createAutoLease(Duration.ofSeconds(1)).get(1, TimeUnit.SECONDS);
            etcDSharedDataAccess.register(data, unLeaser);
            Thread.sleep(2000);
            Assert.assertEquals("blabla", etcDSharedDataAccess.get(Data1.TYPE, data)
                    .get(1, TimeUnit.SECONDS)
                    .orElseThrow().get(Data1.someData));
            unLeaser.end();
            Thread.sleep(3000);
            List<Glob> globs = etcDSharedDataAccess.getUnder(Data1.TYPE, FieldValues.EMPTY)
                    .get(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, globs.size());
            etcDSharedDataAccess.end();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } finally {
            Client clientDelete = Client.builder().endpoints(ETCD).build();
            deleteAll(clientDelete);
            clientDelete.close();
        }
    }

    private void checkLease(SharedDataAccess etcDSharedDataAccess) throws InterruptedException, ExecutionException, TimeoutException {
        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        etcDSharedDataAccess.registerWithLease(data, 3, TimeUnit.SECONDS)
                .get(1, TimeUnit.MINUTES);
        Assert.assertEquals("blabla", etcDSharedDataAccess.get(Data1.TYPE, FieldValuesBuilder.init()
                        .set(Data1.shop, "mg.the-oz.com")
                        .set(Data1.workerName, "w1")
                        .set(Data1.num, 1)
                        .get())
                .get(1, TimeUnit.MINUTES)
                .orElseThrow().get(Data1.someData));
        Thread.sleep(4000);
        Assert.assertTrue(etcDSharedDataAccess.get(Data1.TYPE, FieldValuesBuilder.init()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .get()).get(1, TimeUnit.MINUTES).isEmpty());

        SharedDataAccess.UnLeaser unLeaser = etcDSharedDataAccess.registerWithLease(data, 3, TimeUnit.SECONDS)
                .get(1, TimeUnit.MINUTES);
        Thread.sleep(1000);
        unLeaser.touch();
        Thread.sleep(1000);
        unLeaser.touch();
        Thread.sleep(1000);
        unLeaser.touch();
        Thread.sleep(1000);
        unLeaser.touch();
        Assert.assertEquals("blabla", etcDSharedDataAccess.get(Data1.TYPE, FieldValuesBuilder.init()
                        .set(Data1.shop, "mg.the-oz.com")
                        .set(Data1.workerName, "w1")
                        .set(Data1.num, 1)
                        .get())
                .get(1, TimeUnit.MINUTES)
                .orElseThrow().get(Data1.someData));
    }

    public static class Data1 {
        public static GlobType TYPE;
        @FieldNumber_(1)
        @PathIndex_(1)
        public static StringField shop;

        @FieldNumber_(2)
        @PathIndex_(2)
        public static StringField workerName;

        @FieldNumber_(3)
        @PathIndex_(3)
        public static IntegerField num;

        @FieldNumber_(4)
        public static StringField someData;

        static {
            GlobTypeLoaderFactory.create(Data1.class).load();
        }
    }

    public static class Data2 {
        public static GlobType TYPE;
        @FieldNumber_(1)
        @PathIndex_(1)
        public static StringField shop;

        @FieldNumber_(2)
        @PathIndex_(2)
        public static StringField workerName;

        @FieldNumber_(3)
        @PathIndex_(3)
        public static IntegerField num;

        @FieldNumber_(4)
        public static StringField someData;

        static {
            GlobTypeLoaderFactory.create(Data2.class).load();
        }
    }
}