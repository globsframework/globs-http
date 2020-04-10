package org.globsframework.remote.peer;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.globsframework.remote.peer.direct.MessageInfo;
import org.globsframework.remote.peer.direct.MessageType;

public class MessageInfoTest extends TestCase {

    public void testReadWrite() throws Exception {

        for (int i = 0; i < 12541; i++) {
            byte[] bytes = new byte[10];
            MessageInfo.toBytes(MessageType.START_END, -i, i, bytes);
            MessageInfo messageInfo = MessageInfo.read(bytes, 0);
            Assert.assertEquals(-i, messageInfo.id);
            Assert.assertEquals(MessageType.START_END, messageInfo.messageType);
            Assert.assertEquals(i, messageInfo.size);
        }

    }
}
