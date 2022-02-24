package org.globsframework.shared;

import io.etcd.jetcd.Client;
import junit.framework.Assert;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.FieldValuesBuilder;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.serialisation.model.FieldNumber_;
import org.globsframework.shared.model.PathIndex_;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;

public class EtcDSharedDataAccessTest {
    public static final String[] ETCD = new String[]{"http://localhost:2379"}; //, "http://localhost:4001"


    @Test
    @Ignore("integration test to be filtered later")
    public void testNameBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        Client clientRead = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);
        checkPutGet(etcDSharedDataAccess, EtcDSharedDataAccess.createBin(clientRead));
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void testNameJson() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        Client clientRead = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createJson(client);
        checkPutGet(etcDSharedDataAccess, EtcDSharedDataAccess.createJson(clientRead));
    }

    private void checkPutGet(SharedDataAccess etcDSharedDataAccess, SharedDataAccess sharedDataAccessRead) throws InterruptedException, ExecutionException, TimeoutException {
        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        BlockingQueue<Glob> puts = new ArrayBlockingQueue<>(10);
        BlockingQueue<Glob> deletes = new ArrayBlockingQueue<>(10);
        sharedDataAccessRead.listenUnder(Data1.TYPE, new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                puts.add(glob);
            }

            public void delete(Glob glob) {
                deletes.add(glob);
            }
        });

        BlockingQueue<Glob> puts2 = new ArrayBlockingQueue<>(10);
        BlockingQueue<Glob> deletes2 = new ArrayBlockingQueue<>(10);
        sharedDataAccessRead.listenUnder(Data2.TYPE, new SharedDataAccess.Listener() {
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
        sharedDataAccessRead.listen(Data1.TYPE, new SharedDataAccess.Listener() {
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

        etcDSharedDataAccess.end();
        sharedDataAccessRead.end();
    }


    @Test
    @Ignore("integration test to be filtered later")
    public void testLeaseBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);
        checkLease(etcDSharedDataAccess);
    }

    @Test
    @Ignore("integration test to be filtered later")
    public void testLeaseJson() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createJson(client);
        checkLease(etcDSharedDataAccess);
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