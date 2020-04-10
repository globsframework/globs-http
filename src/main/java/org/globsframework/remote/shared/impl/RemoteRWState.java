package org.globsframework.remote.shared.impl;

import org.globsframework.utils.serialization.DefaultSerializationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

class RemoteRWState {
    public static final int MAX_MSG_SIZE = Integer.getInteger("org.globsframework.shared.max.size", 1024 * 1024 * 20);
    public static final int BUFFER_SIZE = Integer.getInteger("org.globsframework.shared.buffer.size", 1024 * 4);
    public static final int MARK = 0x54000000;
    public static final int MASK_FOR_MARK = 0xFC000000;
    public static final int MASK_FOR_SIZE = 0x3FFFFFF; //->67108863 -> 64Mo
    public static final int MAX_BUF_COUNT = 1024;
    static private Logger logger = LoggerFactory.getLogger(RemoteRWState.class);
    private SocketChannel socketChannel;
    private SelectionKey selectionKey;
    private Queue<ByteBuffer> buffers;
    private ByteBuffer headerBuffer;
    private byte[] message = new byte[1024];
    private byte[] header = new byte[4];
    private int currentHeaderRead;
    private int currentPos;
    private int currentMsgSize = -1;
    private volatile boolean closed = false;
    private int clientId;

    RemoteRWState(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        headerBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffers = new LinkedList<>();
    }

    public void cancel() {
        if (!closed) {
            if (selectionKey != null) {
                selectionKey.cancel();
            }
            try {
                socketChannel.close();
            } catch (IOException e) {
                logger.error("Error on socket closed.");
            }
            closed = true;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public void read(MsgCallback callback, int maxMsgToRead) throws IOException {
        if (closed) {
            return;
        }
        int read = socketChannel.read(headerBuffer);
        // thrown only is no message at all were read.
        if (read < 0) {
            String msg = "Connection closed " + " client " + clientId;
            logger.info(msg);
            throw new IOException(msg);
        }
        try {
            while (read > 0) {
                headerBuffer.flip();
                while (headerBuffer.hasRemaining()) {
                    if (currentMsgSize == -1) {
                        int length = Math.min(header.length - currentHeaderRead, headerBuffer.remaining());
                        headerBuffer.get(header, currentHeaderRead, length);
                        currentHeaderRead += length;
                        if (currentHeaderRead == 4) {
                            // not a compressed SerializedInput => we only read an int here
                            // should be SerializedInputOutputFactory.initCompressed
//                            currentMsgSize = SerializedInputOutputFactory.init(header).readNotNullInt();
                            currentMsgSize = DefaultSerializationInput.toInt(header[0] & 0xff, header[1] & 0xff, header[2] & 0xff, header[3] & 0xff);
                            if ((currentMsgSize & MASK_FOR_MARK) != MARK) {
                                String message1 = "Bad message from " + socketChannel.socket().toString() + " client " + clientId;
                                logger.warn(message1);
                                socketChannel.close();
                                throw new IOException(message1);
                            }
                            currentMsgSize &= MASK_FOR_SIZE;
                            if (message.length < currentMsgSize) {
                                if (currentMsgSize > MAX_MSG_SIZE) {
                                    String message1 = "Message too big " + (currentMsgSize / 1024 / 1024) + " Mo (more than " + (MAX_MSG_SIZE / 1024 / 1024) + " property 'globsframework.shared.max.size') " + " client " + clientId;
                                    logger.error(message1);
                                    socketChannel.close();
                                    throw new IOException(message1);
                                }
                                message = new byte[currentMsgSize];
                            }
                        }
                    }
                    if (currentMsgSize >= 0) {
                        int length = Math.min(currentMsgSize - currentPos, headerBuffer.remaining());
                        if (length > 0) {
                            headerBuffer.get(message, currentPos, length);
                            currentPos += length;
                        }
                    }
                    if (currentMsgSize == currentPos) {
                        callback.msg(message, currentMsgSize);
                        currentMsgSize = -1;
                        currentPos = 0;
                        currentHeaderRead = 0;
                        maxMsgToRead--;
                        if (!headerBuffer.hasRemaining()) { //to prevent one system call if the message was fully read and there is no remaining data in buffer.
                            // (it can be wrong if the max size of the buffer equals the size of the message)
                            headerBuffer.clear();
                            return;
                        }
                    }
                }
                headerBuffer.clear();
                if (maxMsgToRead < 0) {
                    return;
                }
                try {
                    read = socketChannel.read(headerBuffer);
                } catch (IOException e) {
                    // do not throw error to allow the previous message to be computed properly.
                    logger.info("io exception connection will be closed for " + clientId);
                    return;
                }
            }
        } catch (IOException e) {
            String msg = "receive io exception " + (e.getMessage() != null ? e.getMessage() : "") + " ; client " + clientId;
            logger.info(msg);
            throw new IOException(msg, e);
        }
    }

    public boolean write(byte[] bytesToSend, int len) throws IOException {
        if (closed) {
            logger.info("No write (closed), client " + clientId);
            return true;
        }
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytesToSend, 0, len);
            if (buffers.isEmpty()) {
                socketChannel.write(byteBuffer);
                if (byteBuffer.hasRemaining()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Buffer partially send, client " + clientId);
                    }
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    buffers.add(byteBuffer);
                    return false;
                }
                logger.debug("Buffer sent");
                return true;
            } else {
                if (buffers.size() > MAX_BUF_COUNT) {
                    buffers.clear();
                    String msg = "Too slow reader : closing connection, client " + clientId;
                    logger.error(msg);
                    throw new IOException(msg);
                }
                logger.debug("Buffer send later");
                buffers.add(byteBuffer);
                return false;
            }
        } catch (IOException e) {
            logger.info("Can not write : " + e.getMessage());
            throw e;
        }
    }

    public void writeNext() throws IOException {
        if (closed) {
            logger.info("No write Next (closed)");
            return;
        }
        try {
            logger.debug("Write next");
            ByteBuffer byteBuffer = buffers.peek();
            while (byteBuffer != null) {
                socketChannel.write(byteBuffer);
                if (byteBuffer.hasRemaining()) {
                    return;
                }
                buffers.remove();
                byteBuffer = buffers.peek();
            }
            selectionKey.interestOps(SelectionKey.OP_READ);
        } catch (IOException e) {
            logger.info("Can not write : " + e.getMessage());
            throw e;
        }
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    interface MsgCallback {
        void msg(byte[] msg, int len);
    }
}
