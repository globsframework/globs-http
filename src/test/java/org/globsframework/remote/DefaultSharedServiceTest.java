package org.globsframework.remote;

import junit.framework.TestCase;
import org.globsframework.directory.DefaultDirectory;
import org.globsframework.directory.Directory;
import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.direct.DirectPeerToPeer;
import org.globsframework.remote.request.Shared;
import org.globsframework.remote.rpc.ExportMethod;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.remote.rpc.impl.DefaultRpcService;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataManager;
import org.globsframework.remote.shared.impl.DefaultSharedDataManager;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedOutput;
import org.junit.Test;

public class DefaultSharedServiceTest extends TestCase {

    @Test
    public void testShared() throws Exception {

        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();

        final Directory directory1 = initComLayerDirectory(serverSharedData, true);
        DefaultRpcService rpcService1 = new DefaultRpcService("worker", new DirectoryProvider() {
            public Directory getDirectory() {
                return directory1;
            }
        }, directory1.get(SharedDataManager.class), directory1.get(PeerToPeer.class));
        directory1.add(RpcService.class, rpcService1);
        DefaultSharedService.initUniqueId(rpcService1);
        final SharedService sharedService1 = new DefaultSharedService(rpcService1);
        directory1.add(SharedService.class, sharedService1);

        final Directory directory2 = initComLayerDirectory(serverSharedData, false);
        DefaultRpcService rpcService2 = new DefaultRpcService("worker", new DirectoryProvider() {
            public Directory getDirectory() {
                return directory2;
            }
        }, directory2.get(SharedDataManager.class), directory2.get(PeerToPeer.class));
        directory2.add(RpcService.class, rpcService2);
        SharedService sharedService2 = new DefaultSharedService(rpcService2);
        directory2.add(SharedService.class, sharedService2);

        final DummyObject value = new DummyObject();
        rpcService1.register(DummyRemoteCall.class, new DummyRemoteCallImpl(sharedService1, value), "any", new DummyObjectSerializer());

        DummyRemoteCall any = rpcService2.getService(DummyRemoteCall.class, "any", new DummyObjectSerializer());
        assertSame(any.call(), any.call());
    }


    public static interface DummyRemoteCall {
        @ExportMethod
        public Shared<DummyObject> call();
    }

    public static class DummyObject {

    }

    private Directory initComLayerDirectory(ServerSharedData serverSharedData, boolean registerNamingService) {
        Directory directory = new DefaultDirectory();
        SharedDataManager sharedDataManager = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        if (registerNamingService) {
            DefaultRpcService.registerRpcNamingServiceHere(sharedDataManager);
        }
        directory.add(SharedDataManager.class, sharedDataManager);
        directory.add(PeerToPeer.class, new DirectPeerToPeer());
        return directory;
    }

    private static class DummyObjectSerializer implements Serializer {
        public Class getClassType() {
            return DummyObject.class;
        }

        public Object read(SerializedInput serializedInput, Directory directory) {
            return new DummyObject();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
        }
    }

    public static class DummyRemoteCallImpl implements DummyRemoteCall {
        private final SharedService sharedService1;
        private final DummyObject value;

        public DummyRemoteCallImpl(SharedService sharedService1, DummyObject value) {
            this.sharedService1 = sharedService1;
            this.value = value;
        }

        public Shared<DummyObject> call() {
            return sharedService1.declare(value);
        }
    }
}
