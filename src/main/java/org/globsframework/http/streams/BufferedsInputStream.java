package org.globsframework.http.streams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class BufferedsInputStream extends InputStream {
    private static final byte[] LAST_EMPTY_BUFFER = new byte[0];
    private final Queue<byte[]> dataQueue = new LinkedList<>();
    private byte[] currentBuffer = null;
    private int currentIndex = 0; // Current position within the current buffer
    private boolean closed = false;

    public int read() {
        while (true) {
            if (closed) {
                throw new IllegalStateException("Stream is closed");
            }
            if (currentBuffer != null && currentIndex < currentBuffer.length) {
                return currentBuffer[currentIndex++] & 0xFF; // Read a byte from the current buffer
            }
            if (!readFromNextBuffer()){
                return -1;
            }
        }
    }

    private synchronized boolean readFromNextBuffer() {
        while (!closed) {
            if (!dataQueue.isEmpty()) {
                currentBuffer = dataQueue.poll();
                currentIndex = 0; // Reset index for the new buffer
                return currentBuffer != LAST_EMPTY_BUFFER;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while reading", e);
            }
        }
        throw new IllegalStateException("Stream is closed");
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException("Buffer is null");
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }

        if (closed) {
            throw new IllegalStateException("Stream is closed");
        }
        int bytesRead = 0;
        while (bytesRead == 0) {
            if (currentBuffer != null && currentIndex < currentBuffer.length) {
                int toRead = Math.min(len, currentBuffer.length - currentIndex);
                System.arraycopy(currentBuffer, currentIndex, b, off, toRead);
                currentIndex += toRead;
                bytesRead += toRead;
                off += toRead;
                len -= toRead;
                continue;
            }
            if (!readFromNextBuffer()){
                return -1;
            }
        }
        return bytesRead;
    }

    public synchronized void newBuffer(ByteBuffer byteBuffer, boolean lastBuffer) {
        if (closed) {
            throw new IllegalStateException("Stream is closed");
        }
        if (byteBuffer != null && byteBuffer.hasRemaining()) {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            dataQueue.add(bytes);
        }
        if (lastBuffer) {
            dataQueue.add(LAST_EMPTY_BUFFER);
        }
        notifyAll();
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}
