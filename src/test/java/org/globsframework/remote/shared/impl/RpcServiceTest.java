package org.globsframework.remote.shared.impl;

import junit.framework.TestCase;
import org.globsframework.directory.DefaultDirectory;
import org.globsframework.directory.Directory;
import org.globsframework.remote.DefaultSharedService;
import org.globsframework.remote.DirectoryProvider;
import org.globsframework.remote.SharedService;
import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.remote.rpc.ExportMethod;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.remote.rpc.impl.DefaultRpcService;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataManager;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.Ref;
import org.globsframework.utils.TestUtils;
import org.globsframework.utils.collections.MultiMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RpcServiceTest extends TestCase {
    DefaultDirectory directory;

    static {
//        Log4j.initLog();
        System.setProperty("mra.peer.buffer.size", "300");
    }

    public void setUp() throws Exception {
        super.setUp();
        directory = new DefaultDirectory();
    }

    public void tearDown() throws Exception {
        super.tearDown();
        directory.clean();
    }

    public void testName() throws Exception {
        directory.add(PeerToPeer.class, new DirectPeerToPeer());
        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
        SharedDataManager sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(serverSharedData.getHost(), serverSharedData.getPort()));
        directory.add(SharedDataManager.class, sharedDataManager);
        DefaultRpcService.registerRpcNamingServiceHere(sharedDataManager);
        RpcService rpcService1 = new DefaultRpcService("worker", new DirectoryProvider() {
            public Directory getDirectory() {
                return directory;
            }
        }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
        directory.add(SharedService.class, new DefaultSharedService(0));
        rpcService1.register(TestRpcCall.class, new TestRpcCallImpl(), "toto");

        RpcService rpcService2 = new DefaultRpcService("worker", new DirectoryProvider() {
            public Directory getDirectory() {
                return directory;
            }
        }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
        TestRpcCall toto = rpcService2.getService(TestRpcCall.class, "toto");
        assertEquals(-3, toto.callMe(3));
        TestUtils.assertEquals(rpcService2.listService(TestRpcCall.class), "toto");


        NanoChrono chrono = NanoChrono.start();
        for (int i = 0; i < 100000; i++) {
            assertEquals(-i, toto.callMe(i));
        }
        System.out.println("RpcServiceTest.testName " + 100000. * 1000. / chrono.getElapsedTimeInMS() + " call/s"); //21000

        chrono = NanoChrono.start();
        Ref<Integer> aRef = new Ref<Integer>();
        for (int i = 0; i < 100000; i++) {
            aRef.set(i);
            assertEquals(-i, toto.callInOut(aRef));
            assertEquals(-i, aRef.get().intValue());
        }
        System.out.println("RpcServiceTest.testName " + 100000. * 1000. / chrono.getElapsedTimeInMS() + " call/s"); //26000

        aRef.reset();
        assertEquals(0, toto.callInOut(aRef));

        MultiMap<String, String> multiMap = new MultiMap<String, String>();
        multiMap.put("A", "A1");
        multiMap.put("A", "A2");
        multiMap.put("B", "B2");
        toto.callMultiMap(multiMap);

        Ref<List<String>> a = new Ref<List<String>>(new ArrayList<String>());
        a.get().add("titi");
        toto.callInOutRefList(a);
        assertEquals("titi", a.get().get(0));
        assertEquals("toto", a.get().get(1));

        ArrayList<String> a1 = new ArrayList<String>();
        a1.add("titi");
        List<String> res = toto.callRetList(a1);
        TestUtils.assertEquals(res, "titi", "toto");

        assertEquals("titi", new String(toto.callArrays("titi".getBytes())));
        assertEquals(10, toto.call(new MySerializableObject(5)).value);

        assertEquals(MyEnum.B, toto.call(MyEnum.C));
    }

    enum MyEnum {
        A,B,C
    }

    interface TestRpcCall {

        @ExportMethod
        int callMe(int a);

        @ExportMethod
        int callInOut(Ref<Integer> a);

        @ExportMethod
        void callMultiMap(MultiMap<String, String> multiMap);

        @ExportMethod
        int callInOutRefList(Ref<List<String>> a);

        @ExportMethod
        List<String> callRetList(List<String> a);

        @ExportMethod
        byte[] callArrays(byte[] arg);

        @ExportMethod
        long[] callArrays(int[] arg);

        @ExportMethod
        long[] callArraysOfArray(int[][] arg);

        @ExportMethod
        MySerializableObject call(MySerializableObject object);

        @ExportMethod
        MyEnum call(MyEnum myEnum);

    }


    public static class MySerializableObject implements Serializable {
        private int value;

        public MySerializableObject(int value) {
            this.value = value;
        }
    }

    public static class TestRpcCallImpl implements TestRpcCall {

        public int callMe(int a) {
            return -a;
        }

        public int callInOut(Ref<Integer> a) {
            if(a.get()==null) {
                a.set(0);
                return 0;
            }
            a.set(-a.get());
            return a.get();
        }

        public void callMultiMap(MultiMap<String, String> multiMap) {

        }

        public int callInOutRefList(Ref<List<String>> a) {
            a.get().add("toto");
            return 1;
        }

        public List<String> callRetList(List<String> a) {
            a.add("toto");
            return a;
        }

        public byte[] callArrays(byte[] arg) {
            return arg;
        }

        public long[] callArrays(int[] arg) {
            return new long[0];
        }

        public long[] callArraysOfArray(int[][] arg) {
            return new long[0];
        }

        public MySerializableObject call(MySerializableObject object) {
            return new MySerializableObject(object.value * 2);
        }

        public MyEnum call(MyEnum myEnum) {
            return MyEnum.B;
        }
    }

    final static int COUNT =  50000;

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0){
            final DefaultDirectory directory = new DefaultDirectory();
            directory.add(PeerToPeer.class, new DirectPeerToPeer());
            ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
            SharedDataManager sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(serverSharedData.getHost(), serverSharedData.getPort()));
            directory.add(SharedDataManager.class, sharedDataManager);
            DefaultRpcService.registerRpcNamingServiceHere(sharedDataManager);
            RpcService rpcService = new DefaultRpcService("worker", new DirectoryProvider() {
                public Directory getDirectory() {
                    return directory;
                }
            }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
            rpcService.register(TestRpcCall.class, new TestRpcCallImpl(), "toto");
            System.out.println("RpcServiceTest.main " + serverSharedData.getHost() + " " + serverSharedData.getPort());
            synchronized (directory){
                directory.wait();
            }
        }
        else {
            final DefaultDirectory directory = new DefaultDirectory();
            directory.add(PeerToPeer.class, new DirectPeerToPeer());
            SharedDataManager sharedDataManager = DefaultSharedDataManager.create(AddressAccessor.FixAddressAccessor.create(args[0], Integer.parseInt(args[1])));
            directory.add(SharedDataManager.class, sharedDataManager);
            RpcService rpcService = new DefaultRpcService("worker", new DirectoryProvider() {
                public Directory getDirectory() {
                    return directory;
                }
            }, directory.get(SharedDataManager.class), directory.get(PeerToPeer.class));
            TestRpcCall toto = rpcService.getService(TestRpcCall.class, "toto");
            for (int i = 0; i < COUNT; i++) {
                assertEquals(-i, toto.callMe(i));
            }
            NanoChrono chrono = NanoChrono.start();
            for (int i = 0; i < COUNT; i++) {
                assertEquals(-i, toto.callMe(i));
            }
            System.out.println("RpcServiceTest.testName " + ((double) COUNT) * 1000. / chrono.getElapsedTimeInMS() + " call/s");
        }
    }

}
