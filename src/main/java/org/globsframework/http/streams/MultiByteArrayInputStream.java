package org.globsframework.http.streams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class MultiByteArrayInputStream extends InputStream {
    private List<byte[]> buffer = new LinkedList<>();
    private byte[] currentBuffer;
    private int currentPos = 0;
    private int size = 0;

    void addBuffer(byte[] buffer) {
        if (buffer == null || buffer.length == 0) {
            return;
        }
        if (currentBuffer == null) {
            currentBuffer = buffer;
            currentPos = 0;
        } else {
            this.buffer.add(buffer);
        }
        this.size += buffer.length;
    }

    public int read() throws IOException {
        if (currentBuffer == null) {
            return -1;
        }
        if (currentPos >= currentBuffer.length) {
            if (buffer.isEmpty()) {
                currentBuffer = null;
                currentPos = 0;
                return -1;
            }
            currentBuffer = buffer.remove(0);
            currentPos = 0;
        }
        return currentBuffer[currentPos++] & 0xFF;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (currentBuffer == null) {
            return -1;
        }
        if (currentPos >= currentBuffer.length) {
            if (buffer.isEmpty()) {
                currentBuffer = null;
                currentPos = 0;
                return -1;
            }
            currentBuffer = buffer.remove(0);
            currentPos = 0;
        }
        int read = Math.min(len, currentBuffer.length - currentPos);
        System.arraycopy(currentBuffer, currentPos, b, off, read);
        currentPos += read;
        return read;
    }

    public void addBuffer(ByteBuffer src) {
        byte[] dst = new byte[src.remaining()];
        src.get(dst);
        addBuffer(dst);
    }
}
