package org.globsframework.remote.shared.impl;

import junit.framework.TestCase;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataManager;
import org.globsframework.remote.shared.SharedDataService;
import org.globsframework.utils.Utils;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@Ignore("Need rework")
public class SharedDataManagerTest {

    static {
//        Log4j.initLog();
    }

    @Ignore
    @Test
    public void testReset() throws Exception {
        ServerSharedData serverSharedData = DefaultSharedDataManager.initSharedData();
        SharedDataManager centralSharedDataManager = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataManager.Path path = SharedPathBuilder.create("/test");
        DefaultGlobModel model = new DefaultGlobModel(DefaultSharedDataManagerTest.SharedDummyObject1.TYPE);
        centralSharedDataManager.create(path, model);


        SharedDataManager sharedDataManager1 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        SharedDataService sharedDataServiceSync = sharedDataManager1.getSharedDataServiceSync(path, model);

        TestSharedDataEventListener sharedDataEventListener = new TestSharedDataEventListener();
        sharedDataServiceSync.listen(sharedDataEventListener);

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            stringBuilder.append("Some long long string ");
        }
        final String s = stringBuilder.toString();

        final SharedDataManager sharedDataManager2 = DefaultSharedDataManager.create(new AddressAccessor.FixAddressAccessor(serverSharedData.getHost(), serverSharedData.getPort()));
        final SharedDataService write = sharedDataManager2.getSharedDataServiceSync(path, model);
        for (int i = 0; i < RemoteRWState.MAX_BUF_COUNT * 10; i++) {
            final int finalI = i;
            write.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    Key key = KeyBuilder.create(DefaultSharedDataManagerTest.SharedDummyObject1.SHARE_ID, write.getId(),
                            DefaultSharedDataManagerTest.SharedDummyObject1.ID, finalI % 1000);
                    globRepository.findOrCreate(key);
                    globRepository.update(key, FieldValue.value(DefaultSharedDataManagerTest.SharedDummyObject1.LABEL1, s + finalI));
                }
            });
        }
        sharedDataEventListener.received();
    }

    private static class TestSharedDataEventListener implements SharedDataService.SharedDataEventListener {
        boolean rec;
        public void event(ChangeSet changeSet) {
            try {
                Thread.sleep(10000);
                System.out.println("TestSharedDataEventListener.event release lock");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void reset() {
            rec = true;
            notifyAll();
            System.out.println("SharedDataManagerTest.reset");
        }

        public void received() {
            synchronized (this){
                Utils.doWait(this, 15000);
            }
            assertTrue(rec);
        }
    }
}
