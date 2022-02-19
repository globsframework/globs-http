package org.globsframework.shared;

import io.etcd.jetcd.Client;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.FieldValuesBuilder;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.serialisation.model.FieldNumber_;
import org.globsframework.shared.model.PathIndex_;

import java.util.List;
import java.util.concurrent.*;

public class EtcDSharedDataAccessTest extends TestCase {
    public static final String[] ETCD = new String[]{"http://localhost:2379"}; //, "http://localhost:4001"


    public void testNameBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);
        checkPutGet(etcDSharedDataAccess);
    }

    public void testNameJson() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();

        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createJson(client);
        checkPutGet(etcDSharedDataAccess);
    }

    private void checkPutGet(SharedDataAccess etcDSharedDataAccess) throws InterruptedException, ExecutionException, TimeoutException {
        MutableGlob data = Data1.TYPE.instantiate()
                .set(Data1.shop, "mg.the-oz.com")
                .set(Data1.workerName, "w1")
                .set(Data1.num, 1)
                .set(Data1.someData, "blabla");

        BlockingQueue<Glob> puts = new ArrayBlockingQueue<>(10);
        BlockingQueue<Glob> deletes = new ArrayBlockingQueue<>(10);
        etcDSharedDataAccess.listenUnder(Data1.TYPE, new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                puts.add(glob);
            }

            public void delete(Glob glob) {
                deletes.add(glob);
            }
        });


        etcDSharedDataAccess.register(data)
                .get(1, TimeUnit.MINUTES);

        Assert.assertEquals("blabla", etcDSharedDataAccess.get(Data1.TYPE, FieldValuesBuilder.init()
                        .set(Data1.shop, "mg.the-oz.com")
                        .set(Data1.workerName, "w1")
                        .set(Data1.num, 1)
                        .get())
                .get(1, TimeUnit.MINUTES)
                .orElseThrow().get(Data1.someData));

        Assert.assertNotNull(puts.poll(1, TimeUnit.MINUTES));

        data.set(Data1.num, 2);
        etcDSharedDataAccess.listen(Data1.TYPE, new SharedDataAccess.Listener() {
            public void put(Glob glob) {
                puts.add(glob);
            }

            public void delete(Glob glob) {
                deletes.add(glob);
            }
        }, data);

        etcDSharedDataAccess.register(data);

        Assert.assertNotNull(puts.poll(1, TimeUnit.MINUTES));

        List<Glob> actual = etcDSharedDataAccess.getUnder(Data1.TYPE, FieldValuesBuilder.init()
                        .set(Data1.shop, "mg.the-oz.com")
                        .set(Data1.workerName, "w1")
                        .get())
                .get(1, TimeUnit.MINUTES);
        Assert.assertEquals(2, actual.size());

        etcDSharedDataAccess.end();
    }


    public void testLeaseBin() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = Client.builder().endpoints(ETCD).build();
        SharedDataAccess etcDSharedDataAccess = EtcDSharedDataAccess.createBin(client);
        checkLease(etcDSharedDataAccess);
    }

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
}