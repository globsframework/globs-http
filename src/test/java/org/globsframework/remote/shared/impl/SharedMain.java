package org.globsframework.remote.shared.impl;

import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.remote.shared.AddressAccessor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.remote.shared.SharedDataService;
import org.globsframework.utils.NanoChrono;
import org.globsframework.utils.TestUtils;

import java.util.concurrent.atomic.AtomicLong;

public class SharedMain {

    public static void main(String[] args) throws InterruptedException {
        final GlobModel globModel = new DefaultGlobModel(SourceLocation.TYPE);

        if (args.length == 0) {
            ServerSharedData serverSharedData = new DefaultServerSharedData(globModel, "localhost", 46223, "/");
            String host = serverSharedData.getHost();
            int port = serverSharedData.getPort();
            System.out.println("SharedMain.main running at " + host + " " + port);
            final SharedDataService clientSharedData = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create(host, port), ClientSharedData.OnStop.NULL, "/");
            clientSharedData.listen(new EventCountSharedDataEventListener());
            synchronized (serverSharedData) {
                serverSharedData.wait();
            }
        } else {
            String host = args[0];
            int port = 46223; //Integer.parseInt(args[1]);

            final SharedDataService clientSharedData = new ClientSharedData(globModel, AddressAccessor.FixAddressAccessor.create(host, port), ClientSharedData.OnStop.NULL, "/");
            if (!clientSharedData.waitForInitialization(10 * 1000)) {
                throw new RuntimeException("Can not connect to server");
            }
            NanoChrono chrono = NanoChrono.start();
            clientSharedData.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    Glob src = globRepository.create(SourceLocation.TYPE,
                            FieldValue.value(SourceLocation.ID, 0),
                            FieldValue.value(SourceLocation.COUNT, 0),
                            FieldValue.value(SourceLocation.LAST_UPDATE_TIME, System.currentTimeMillis()),
                            FieldValue.value(SourceLocation.SOURCE_NAME, "src"),
                            FieldValue.value(SourceLocation.SHARED_ID, clientSharedData.getId()));
                }
            });

            System.out.println("DiffEventListener.reset " + chrono.getElapsedTimeInMS());
            Thread.sleep(1000);
            DiffEventListener sharedDataEventListener = new DiffEventListener(clientSharedData, 100);
            clientSharedData.listen(sharedDataEventListener);

            int milliPerMsg = 1000 / 500 ;

            while (true) {
                final long startLoop = System.currentTimeMillis();
                clientSharedData.write(new SharedDataService.SharedData() {
                    public void data(GlobRepository globRepository) {
                        Key key = KeyBuilder.create(SourceLocation.ID, 0, SourceLocation.SHARED_ID, clientSharedData.getId());
                        globRepository.update(key, SourceLocation.LAST_UPDATE_TIME, startLoop);
                    }
                });
                sharedDataEventListener.write();

                long current = System.currentTimeMillis();
                long l = current - startLoop;
                while (l < milliPerMsg) {
                    Thread.sleep(milliPerMsg - l);
                    current = System.currentTimeMillis();
                    l = current - startLoop;
                }
                if (current - startLoop > 2 * milliPerMsg) {
                    System.out.println("long write");
                }
            }
        }
    }

    private static class EventCountSharedDataEventListener implements SharedDataService.SharedDataEventListener, ChangeSetVisitor {
        int eventCount = 0;
        long last = System.currentTimeMillis();
        long current;

        public void event(ChangeSet changeSet) {
            eventCount++;
            current = System.currentTimeMillis();
            changeSet.safeVisit(SourceLocation.TYPE, this);
            if ((current - last) > 1000 * 4) {
                System.out.println("EventCountSharedDataEventListener.event " + (((double) eventCount * 1000.) / (( current - last))) + "msg/s");
                eventCount = 0;
                last = current;
            }
        }

        public void reset() {
        }

        public void visitCreation(Key key, FieldsValueScanner values) throws Exception {

        }

        public void visitUpdate(Key key, FieldsValueWithPreviousScanner values) throws Exception {
            long diff = current - ((Long) TestUtils.get(values, SourceLocation.LAST_UPDATE_TIME));
            if (diff > 20){
                System.out.println("EventCountSharedDataEventListener.visitUpdate " + diff + " at " + current);
            }

        }

        public void visitDeletion(Key key, FieldsValueScanner previousValues) throws Exception {

        }
    }

    private static class DiffEventListener implements SharedDataService.SharedDataEventListener, ChangeSetVisitor {
        long count = 0;
        long somme;
        long tab[] = new long[1000];
        long countTotalDiff;
        private long lastOutputTimeStamp = -1;
        private AtomicLong atomicLong = new AtomicLong(0);
        private SharedDataService clientSharedData;
        private int minEventCount;
        long total = 0;

        public DiffEventListener(SharedDataService clientSharedData, int minEventCount) {
            this.clientSharedData = clientSharedData;
            this.minEventCount = minEventCount;
        }

        public void event(ChangeSet changeSet) {
            changeSet.safeVisit(SourceLocation.TYPE, this);
        }

        public void reset() {
        }

        public void visitCreation(Key key, FieldsValueScanner values) throws Exception {

        }

        public void visitUpdate(Key key, FieldsValueWithPreviousScanner values) throws Exception {
            if (key.get(SourceLocation.SHARED_ID) == clientSharedData.getId()){
                return;
            }
            long currentEventTimeStamp = System.currentTimeMillis();
            int diff = (int) (currentEventTimeStamp - ((Long) TestUtils.get(values, SourceLocation.LAST_UPDATE_TIME)));
            if (minEventCount != 0) {
                lastOutputTimeStamp = currentEventTimeStamp;
                minEventCount--;
                return;
            }
            if (diff > 20) {
                System.out.println("DiffEventListener.visitUpdate : late event : " + diff + " at " + currentEventTimeStamp + " for sharedId : " + key.get(SourceLocation.SHARED_ID));
            }
            total++;
            somme++;
            tab[diff]++;
            countTotalDiff += diff;
            count++;
            if (currentEventTimeStamp - lastOutputTimeStamp > 10*1000){
                double v = (currentEventTimeStamp - lastOutputTimeStamp) / 1000.;
                long wanted = (long) (somme * 0.999);
                long worst = 0;
                int indiceBad = 0;
                int firstWorst = 0;
                int secondWorst = 0;
                for (int i1 = 0; i1 < tab.length; i1++) {
                    long i = tab[i1];
                    worst += i;
                    if (worst < wanted) {
                        indiceBad = i1 + 1;
                    }
                    if (i != 0) {
                        secondWorst = firstWorst;
                        firstWorst = i1;
                    }
                }
                System.out.println("SharedMain.main " + atomicLong.get() + " write");
                System.out.println("SharedMain.main " + tab[0] + " " + tab[1] + " " + tab[2] + " " + tab[3] + " " + tab[4] + " " + tab[5] + " count = " + count + " read in " + v + "s => " + (count / v) + " msg/s");
                System.out.println("SharedMain.main mean : " + countTotalDiff / somme + " execess 99.9% " + indiceBad + " first worst " + firstWorst + "ms 2nd worst " + secondWorst + "ms");
                count = 0;
                atomicLong.set(0);
                lastOutputTimeStamp = currentEventTimeStamp;
            }
        }

        public void visitDeletion(Key key, FieldsValueScanner previousValues) throws Exception {

        }

        public void write() {
            atomicLong.incrementAndGet();
        }
    }
}
