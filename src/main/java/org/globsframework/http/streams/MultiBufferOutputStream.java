package org.globsframework.http.streams;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class MultiBufferOutputStream extends OutputStream {
    int size = 0;
    List<ByteBuffer> buffers = new LinkedList<>();
    ByteBuffer currentBuffer = ByteBuffer.allocateDirect(1024);

    public MultiBufferOutputStream() {
        buffers.add(currentBuffer);
    }

    public void write(int i) throws IOException {
        if (currentBuffer.remaining() == 0) {
            currentBuffer.flip();
            currentBuffer = ByteBuffer.allocateDirect(getCapacity());
            buffers.add(currentBuffer);
        }
        currentBuffer.put((byte)i);
        size++;
    }

    private int getCapacity() {
        return Math.min(currentBuffer.capacity() * 2, 1024 * 1024);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int remaining = currentBuffer.remaining();
            if (remaining == 0) {
                currentBuffer.flip();
                currentBuffer = ByteBuffer.allocateDirect(getCapacity());
                buffers.add(currentBuffer);
            }
            int write = Math.min(len, remaining);
            currentBuffer.put(b, off, write);
            off += write;
            len -= write;
            size += write;
        }
    }

    public long size() {
        return size;
    }

    public void close(){
        currentBuffer.flip();
    }

    public List<ByteBuffer> data() {
        return buffers;
    }
}
