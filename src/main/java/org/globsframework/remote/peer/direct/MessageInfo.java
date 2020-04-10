package org.globsframework.remote.peer.direct;


public class MessageInfo {
    static public final int SIZE = 9;
    public final int id;
    public final int size;
    public final MessageType messageType;

    public MessageInfo(int id, int size, MessageType messageType) {
        this.id = id;
        this.messageType = messageType;
        this.size = size;
    }

    public static MessageInfo read(byte[] data, int at) {
        int value = (data[at + 0] & 0xFF);
        MessageType messageType;
        if (value == 1) {
            messageType = MessageType.START;
        } else if (value == 2) {
            messageType = MessageType.END;
        } else if (value == 3) {
            messageType = MessageType.CONTENT;
        } else if (value == 4) {
            messageType = MessageType.CANCEL;
        } else if (value == 5) {
            messageType = MessageType.START_END;
        } else if (value == 6) {
            messageType = MessageType.PING;
        } else if (value == 7) {
            messageType = MessageType.WAIT;
        } else {
            throw new InvalidMessage("invalid message " + value);
        }
        int size = (((data[at + 1] & 0xFF) << 24) + ((data[at + 2] & 0xFF) << 16) + ((data[at + 3] & 0xFF) << 8) + (data[at + 4] & 0xFF));
        if ((size & 0x7C000000) != 0x54000000) {
            String message = "Bad message signature";
            throw new InvalidMessage(message);
        }
        size &= 0x83FFFFFF;

        int id = (((data[at + 5] & 0xFF) << 24) + ((data[at + 6] & 0xFF) << 16) + ((data[at + 7] & 0xFF) << 8) + (data[at + 8] & 0xFF));
        return new MessageInfo(id, size, messageType);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageInfo that = (MessageInfo) o;

        if (id != that.id) return false;

        return true;
    }

    public int hashCode() {
        return id;
    }

    static public void toBytes(MessageType messageType, int id, int size, byte[] bytes) {
        bytes[0] = (byte) ((messageType.id) & 0xFF);

        size = size | 0x54000000;

        bytes[1] = (byte) ((size >>> 24) & 0xFF);
        bytes[2] = (byte) ((size >>> 16) & 0xFF);
        bytes[3] = (byte) ((size >>> 8) & 0xFF);
        bytes[4] = (byte) ((size >>> 0) & 0xFF);

        bytes[5] = (byte) ((id >>> 24) & 0xFF);
        bytes[6] = (byte) ((id >>> 16) & 0xFF);
        bytes[7] = (byte) ((id >>> 8) & 0xFF);
        bytes[8] = (byte) ((id >>> 0) & 0xFF);
    }
}
