package org.globsframework.remote.peer.direct;

import org.globsframework.directory.Cleanable;
import org.globsframework.directory.Directory;
import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.ServerListener;
import org.globsframework.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectPeerToPeer implements PeerToPeer, Cleanable {
    public static final int TIMEOUT_BEFORE_PING = Integer.getInteger("org.globsframework.peer.direct.ping", 1000 * 60 * 2);
    public static final int GRACE_PERIOD = Integer.getInteger("org.globsframework.peer.direct.timeout.before.release", 1000 * 60);
    public static final int CACHE_OF_CLIENT_REQUEST = Integer.getInteger("org.globsframework.peer.direct.client.request.cache", 3);
    public static final int CACHE_MAX_OF_CLIENT_REQUEST = Integer.getInteger("org.globsframework.peer.direct.client.request.cache.max", 12);
    public static final byte[] EMPTY_DATA = new byte[0];
    public static final int MAX_BUFFER_COUNT = Integer.getInteger("org.globsframework.peer.buffer.count", 5);
    public static final boolean TCP_NO_DELAY = Boolean.parseBoolean(System.getProperty("org.globsframework.peer.direct.setTcpNoDelay", "true"));
    public static final int BUFFER_SIZE = Integer.getInteger("org.globsframework.peer.buffer.read.size", 512 * 1024);
    public static final int BUFFER_READ_SIZE = Integer.getInteger("org.globsframework.peer.buffer.read.size", BUFFER_SIZE);
    public static final int BUFFER_WRITE_SIZE = Integer.getInteger("org.globsframework.peer.buffer.write.size", BUFFER_SIZE);
    public static final int TIMEOUT_CLOSE = Integer.getInteger("org.globsframework.peer.direct.timeout.close", 60 * 1000);

    private static Logger log = LoggerFactory.getLogger(DirectPeerToPeer.class);
    private static PendingBuffers pendingWriteBuffers = new PendingBuffers(BUFFER_WRITE_SIZE);
    private static PendingBuffers pendingReadBuffers = new PendingBuffers(BUFFER_READ_SIZE);
    private static Timer timer = new Timer(true);

    private final String hostName;
    private Map<String, DirectClientRequestFactory> clients = new ConcurrentHashMap<>();
    private ConnectionChecker connectionChecker;

    public DirectPeerToPeer() {
        try {
            connectionChecker = new ConnectionChecker();
            timer.scheduleAtFixedRate(connectionChecker, TIMEOUT_BEFORE_PING, TIMEOUT_BEFORE_PING);
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Can not known host name ", e);
        }
    }

    public DirectPeerToPeer(String hostName) {
        connectionChecker = new ConnectionChecker();
        timer.scheduleAtFixedRate(connectionChecker, TIMEOUT_BEFORE_PING, TIMEOUT_BEFORE_PING);
        this.hostName = hostName;
    }

    static private OutputStream trace(OutputStream outputStream, String name) {
        return outputStream;
//        return new TraceOutputStream(outputStream, name);
    }

    static private InputStream trace(InputStream inputStream, String name) {
        return inputStream;
//        return new TraceInputStream(inputStream, name);
    }

    public ServerListener createServerListener(int threadCount, ServerRequestFactory serverRequestFactory, String name) {
        return new DirectServerListener(hostName, 0, serverRequestFactory, name);
    }

    public ServerListener createServerListener(int port, int threadCount, ServerRequestFactory serverRequestFactory, String name) {
        return new DirectServerListener(hostName, port, serverRequestFactory, name);
    }

    public synchronized ClientRequestFactory getClientRequestFactory(String url) {
        final ClientRequestFactory clientRequestFactory = clients.get(url);
        if (clientRequestFactory == null) {
            final DirectClientRequestFactory directClientRequestFactory = new DirectClientRequestFactory(url);
            clients.put(url, directClientRequestFactory);
            return new ClientRequestFactory() {
                public ClientRequest create() {
                    return directClientRequestFactory.create();
                }

                public void release() {
                }
            };
        } else {
            return new ClientRequestFactory() {
                public ClientRequest create() {
                    return clientRequestFactory.create();
                }

                public void release() {
                }
            };
        }
    }

    public synchronized void destroy() {
        for (ClientRequestFactory clientRequestFactory : clients.values()) {
            clientRequestFactory.release();
        }
        if (connectionChecker != null) {
            connectionChecker.cancel();
            connectionChecker = null;
        }
    }

    public void clean(Directory directory) {
        destroy();
    }

    static class SocketListener implements Runnable {
        private final String hostName;
        private final ServerRequestFactory serverRequestFactory;
        ServerSocket serverSocket;
        SocketAddress localSocketAddress;
        volatile boolean closedRequest = false;
        volatile boolean complete = false;
        ExecutorService executor = Executors.newCachedThreadPool();
        private Set<ClientRequestListener> clientRequestListeners = new HashSet<>();

        SocketListener(String hostName, ServerRequestFactory serverRequestFactory) {
            this(hostName, 0, serverRequestFactory);
        }

        SocketListener(String hostName, int port, ServerRequestFactory serverRequestFactory) {
            this.hostName = hostName;
            this.serverRequestFactory = serverRequestFactory;
            try {
                serverSocket = new ServerSocket(port);
                localSocketAddress = serverSocket.getLocalSocketAddress();
            } catch (IOException e) {
                String message = "in socket listener : " + localSocketAddress;
                log.error(message, e);
                throw new RuntimeException(message, e);
            }
        }

        public void run() {
            try {
                while (!closedRequest) {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(TCP_NO_DELAY);
                    ClientRequestListener clientRequestListener = new ClientRequestListener(serverRequestFactory.createServerRequest(), client, this);
                    synchronized (this) {
                        clientRequestListeners.add(clientRequestListener);
                    }
                    executor.execute(clientRequestListener);
                }
            } catch (IOException e) {
                if (closedRequest) {
                    log.info("End server");
                } else {
                    log.warn("End server with io error ", e);
                }
            } finally {
                synchronized (this) {
                    complete = true;
                    notifyAll();
                }
            }
        }

        public void stop() {
            try {
                closedRequest = true;
                clean();
                serverSocket.close();
                Utils.doWait(this, TIMEOUT_CLOSE, new Utils.Condition() {
                    public boolean call() {
                        return complete;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Fail to close server socket : " + localSocketAddress, e);
            }
        }

        public void clean() throws IOException {
            synchronized (this) {
                for (ClientRequestListener clientRequestListener : clientRequestListeners) {
                    clientRequestListener.socket.close();
                }
            }
            executor.shutdownNow();
        }

        public String getUrl() {
            return "tcp://" + hostName + ":" + serverSocket.getLocalPort();
        }

        private void remove(ClientRequestListener clientRequestListener) {
            synchronized (this) {
                clientRequestListeners.remove(clientRequestListener);
            }
        }
    }

    private static class DirectServerListener implements ServerListener {
        SocketListener socketListener;
        Thread thread;

        public DirectServerListener(String hostName, int port, ServerRequestFactory serverRequestFactory, String name) {
            socketListener = new SocketListener(hostName, port, serverRequestFactory);
            thread = new Thread(socketListener, name + " on " + socketListener.getUrl());
            thread.setDaemon(true);
            thread.start();
        }

        public String getUrl() {
            return socketListener.getUrl();
        }

        public void join() {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted");
            }
        }

        public void stop() {
            socketListener.stop();
        }
    }

    private static class ClientRequestListener implements Runnable {
        private static AtomicInteger atomicInteger = new AtomicInteger(0);
        private final ServerRequestProcessor serverRequestProcessor;
        private final Socket socket;
        private final String socketUrl;
        private SocketListener socketListener;

        public ClientRequestListener(ServerRequestProcessor serverRequestProcessor, Socket socket, SocketListener socketListener) {
            this.serverRequestProcessor = serverRequestProcessor;
            this.socket = socket;
            this.socketListener = socketListener;
            socketUrl = socket.toString() + " (" + atomicInteger.incrementAndGet() + ")";
        }

        public void run() {
            boolean firstCall = true;
            try {
                final ChunckInputStream inputStream = new ChunckInputStream(trace(socket.getInputStream(), socketUrl));
                ChunckedOutputStream outputStream = new ChunckedOutputStream(trace(socket.getOutputStream(), socketUrl));
                if (log.isInfoEnabled()) {
                    log.info("new client from " + socketUrl);
                }
                while (inputStream.nextCall()) {
                    if (inputStream.isPing()) {
                        outputStream.ping();
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("call entry");
                        }
                        outputStream.reset(inputStream.callId);
                        DirectServerRequest serverRequest = new DirectServerRequest(inputStream, socketUrl);
                        DirectServerResponseBuilder response = new DirectServerResponseBuilder(outputStream, inputStream, socketUrl);
                        try {
                            serverRequestProcessor.receive(serverRequest, response);
                        } catch (RuntimeException e) {
                            log.warn("Got exception ", e);
//                            response.complete();
                        }
                        serverRequest.end();
                        response.complete();
                        response.end();
                        if (log.isDebugEnabled()) {
                            log.debug("call complete");
                        }
                    }
                    firstCall = false;
                }
                log.info("end of client call  " + socketUrl + (socket.isClosed() ? " (closed) : " : " (not closed) : "));
            } catch (InvalidMessage invalidMessage) {
                String message = "end of client for " + (socket.isClosed() ? "(closed) : " : "(not closed) : ") + socketUrl;
                if (firstCall) {
                    log.info(message);
                } else {
                    log.warn(message, invalidMessage);
                }
            } catch (Throwable e) {
                String message = "end of client for " + (socket.isClosed() ? "(closed) : " : "(not closed) : ") + socketUrl;
                if (socket.isClosed()) {
                    log.info(message);
                } else {
                    log.warn(message, e);
                }
            } finally {
                this.socketListener.remove(this);
                try {
                    socket.close();
                } catch (IOException e) {
                    log.debug("fail to call close " + socketUrl, e);
                }
            }
        }
    }

    private static class UserInputStream extends InputStream {
        private InputStream inputStream;
        private String socketUrl;
        private Object o;  // do not remove prevent Gc to garbage it's parent until this object is garbageable

        public UserInputStream(ChunckInputStream inputStream, String socketUrl, Object o) {
            this.inputStream = inputStream;
            this.socketUrl = socketUrl;
            this.o = o;
        }

        public int read(byte[] b) throws IOException {
            int read = inputStream.read(b);
            if (read == -1) {
                inputStream = NullInputStream.INSTANCE;
            }
            return read;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int read = inputStream.read(b, off, len);
            if (read == -1) {
                inputStream = NullInputStream.INSTANCE;
            }
            return read;
        }

        public int read() throws IOException {
            int read = inputStream.read();
            if (read == -1) {
                if (log.isDebugEnabled()) {
                    log.debug("EOF reach for " + socketUrl);
                }
                inputStream = NullInputStream.INSTANCE;
            }
            return read;
        }

        public void end() throws IOException {
            if (inputStream != NullInputStream.INSTANCE) {
                if (!isFullyRead()) {
                    throw new RuntimeException("Message not fully read.");
                }
            }
            inputStream = NullInputStream.INSTANCE;
        }

        public boolean isFullyRead() {
            InputStream tmp = inputStream;
            if (tmp != null && tmp instanceof ChunckInputStream) {
                try {
                    while (tmp.read() != -1) ;
                } catch (IOException e) {
                    throw new RuntimeException("EOF reach");
                }
                if (((ChunckInputStream) tmp).isFullyRead()) {
                    ((ChunckInputStream) tmp).releaseBuffer();
                    inputStream = NullInputStream.INSTANCE;
                    return true;
                }
                return false;
            }
            return true;
        }
    }

    private static class UserOutputStream extends OutputStream {
        private ChunckedOutputStream outputStream;
        private String socketUrl;
        private Object parent; // do not remove prevent Gc to garbage it's parent until this object is garbageable

        public UserOutputStream(ChunckedOutputStream outputStream, String socketUrl, Object parent) {
            this.parent = parent;
            this.outputStream = outputStream;
            this.socketUrl = socketUrl;
        }

        public void write(byte[] b) throws IOException {
            outputStream.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            outputStream.write(b, off, len);
        }

        public void write(int b) throws IOException {
            outputStream.write(b);
        }

        public void close() throws IOException {
            if (outputStream != null) {
                try {
                    outputStream.end();
                } catch (IOException e) {
                    throw new IOException(socketUrl, e);
                } finally {
                    outputStream = null;
                }
            }
        }

        public void flush() throws IOException {
            try {
                outputStream.flush();
            } catch (IOException e) {
                throw new IOException(socketUrl, e);
            }
        }

        public void end() throws IOException {
            if (outputStream != null) {
                try {
                    outputStream.end();
                } catch (IOException e) {
                    throw new IOException(socketUrl, e);
                }
            }
            outputStream = null;
        }
    }

    private static class DirectServerRequest implements ServerRequest {
        UserInputStream userInputStream;

        public DirectServerRequest(ChunckInputStream inputStream, String socketUrl) {
            userInputStream = new UserInputStream(inputStream, socketUrl, this);
        }

        public InputStream getRequestStream() {
            return userInputStream;
        }

        public void end() throws IOException {
            userInputStream.end();
        }
    }

    private static class DirectServerResponseBuilder implements ServerResponseBuilder {
        UserOutputStream userOutputStream;
        ChunckInputStream chunckInputStream;

        public DirectServerResponseBuilder(ChunckedOutputStream outputStream, ChunckInputStream inputStream, String socketUrl) {
            chunckInputStream = inputStream;
            userOutputStream = new UserOutputStream(outputStream, socketUrl, this);
        }

        public OutputStream getResponseStream() {
            return userOutputStream;
        }

        public void complete() {
            try {
                userOutputStream.close();
                if (chunckInputStream != null) {
                    if (chunckInputStream.read() != -1) {
                        log.info("response not fully read");
                        byte buffer[] = new byte[1024];
                        while (chunckInputStream.read(buffer, 0, 1024) != -1) ;
                    }
                    chunckInputStream = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Fail to close", e);
            }
        }

        public void end() throws IOException {
            userOutputStream.end();
        }
    }

    static class ChunckInputStream extends InputStream {
        private static MessageInfo WAIT = new MessageInfo(-1, 0, MessageType.WAIT);
        private int currentEnd;
        private int currentPos;
        private InputStream baseInputStream;
        private int callId;
        private int totalMsgLenRead;
        private int bufferSize;
        private MessageInfo header = WAIT;
        private byte[] data = null;

        ChunckInputStream(InputStream baseInputStream) {
            this.baseInputStream = baseInputStream;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (len < 1) {
                return 0;
            }
            if (currentPos + len < currentEnd) {
                System.arraycopy(data, currentPos, b, off, len);
                currentPos += len;
                return len;
            } else {
                return slowRead(b, off, len);
            }
        }

        private int slowRead(byte[] b, int off, int len) throws IOException {
            int read = 0;
            if (currentPos == currentEnd) {
                int readOne = read();
                if (readOne == -1) {
                    return -1;
                }
                b[off++] = (byte) readOne;
                len--;
                read = 1;
            }
            if (currentPos < currentEnd) {
                int canBeRead = Math.min(len, currentEnd - currentPos);
                System.arraycopy(data, currentPos, b, off, canBeRead);
                currentPos += canBeRead;
                read += canBeRead;
            }
            return read;
        }

        public int read() throws IOException {
            if (currentPos < currentEnd) {
                return data[currentPos++] & 0xff;
            }
            return readMore();
        }

        private int readMore() throws IOException {
            if (header == null) {
                throw new RuntimeException("Unexpected EOF for " + callId);
            }
            if (totalMsgLenRead < header.size) {
                int read = baseInputStream.read(data, 0, data.length);
                if (read == -1) {
                    throw new RuntimeException("Unexpected EOF for " + callId);
                }
                currentPos = 0;
                currentEnd = Math.min(read, header.size - totalMsgLenRead);
                totalMsgLenRead += currentEnd;
                bufferSize = read;
                return read();
            }
            if (header.messageType == MessageType.END) {
                return -1;
            }
            if (header.messageType == MessageType.START_END) {
                return -1;
            }
            if (header.messageType == MessageType.WAIT) {
                if (!nextCall()) {
                    return -1;
                }
            } else {
                readNext();
            }
            return read();
        }

        private void readNext() throws IOException {
            header = readHeader();
            if (header.messageType == MessageType.START || header.messageType == MessageType.START_END) {
                throw new RuntimeException("Bug " + header.id + " is start.");
            }
            if (header.id != callId) {
                throw new RuntimeException("Bug " + header.id + " != " + callId);
            }
        }

        private MessageInfo readHeader() throws IOException {
            MessageInfo header = nextHeader();
            if (header == null) {
                throw new RuntimeException("Unexpected EOF for " + callId);
            }
            return header;
        }

        void init() {
            header = WAIT;
            if (data == null || data == EMPTY_DATA) {
                data = pendingReadBuffers.take();
                currentEnd = currentPos = 0;
                bufferSize = 0;
                totalMsgLenRead = 0;
            }
        }

        private MessageInfo nextHeader() throws IOException {
            if (bufferSize - currentPos < MessageInfo.SIZE) {
                System.arraycopy(data, currentEnd, data, 0, bufferSize - currentEnd);
                currentPos = 0;
                bufferSize = bufferSize - currentEnd;
                do {
                    int read = baseInputStream.read(data, bufferSize, data.length - bufferSize);
                    if (read == -1) {
                        log.info("End of stream");
                        pendingReadBuffers.put(data);
                        data = EMPTY_DATA;
                        currentEnd = currentPos = 0;
                        return null;
                    }
                    bufferSize += read;
                } while (bufferSize - currentPos < MessageInfo.SIZE);
            }
            MessageInfo read = MessageInfo.read(data, currentPos);
            currentPos += MessageInfo.SIZE;
            currentEnd = currentPos + Math.min(read.size, bufferSize - currentPos);
            totalMsgLenRead = currentEnd - currentPos;
            return read;
        }

        public boolean nextCall() throws IOException {
            init();
            header = nextHeader();
            if (header == null) {
                return false;
            }
            if (header.messageType != MessageType.START && header.messageType != MessageType.START_END
                    && header.messageType != MessageType.PING) {
                throw new RuntimeException("Bug only start expected " + header.messageType);
            }
            callId = header.id;
            return true;
        }

        public boolean isPing() {
            return header.messageType == MessageType.PING;
        }

        boolean isFullyRead() {
            if (header.messageType == MessageType.END || header.messageType == MessageType.CANCEL || header.messageType == MessageType.START_END) {
                boolean b = totalMsgLenRead == header.size && currentEnd == currentPos;
                if (!b) {
                    log.info(totalMsgLenRead + ", " + header.size + ", " + currentEnd + ", " + currentPos);
                }
                return b;
            }
            if (currentEnd == currentPos) {
                try {
                    header = readHeader();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                boolean b = totalMsgLenRead == header.size && (header.messageType == MessageType.END || header.messageType == MessageType.START_END
                        || header.messageType == MessageType.CANCEL);
                if (!b) {
                    log.info(header.messageType + ", " + totalMsgLenRead + ", " + header.size + ", " + currentEnd + ", " + currentPos);
                }
                return b;
            }
            return false;
        }

        public void releaseBuffer() {
            if (data != EMPTY_DATA && currentEnd == bufferSize) {
                pendingReadBuffers.put(data);
                data = EMPTY_DATA;
            }
        }
    }

    static class ChunckedOutputStream extends OutputStream {
        OutputStream outputStream;
        int id;
        boolean isFirst = true;
        private byte[] data = EMPTY_DATA;
        private int currentPos = MessageInfo.SIZE;

        public ChunckedOutputStream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        final public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        public boolean ping() {
            if (data != EMPTY_DATA) {
                throw new RuntimeException("Bug ping can not be call.");
            }
            try {
                byte[] local = new byte[MessageInfo.SIZE + 8];
                MessageInfo.toBytes(MessageType.PING, 0, 0, local);
                outputStream.write(local, 0, MessageInfo.SIZE);
//                log.info("call write");
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        final public void write(byte[] b, int off, int len) throws IOException {
            if (currentPos + len < data.length) {
                System.arraycopy(b, off, data, currentPos, len);
                currentPos += len;
            } else {
                slowPath(b, off, len);
            }
        }

        private void slowPath(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (currentPos < data.length) {
                    int toWrite = Math.min(len, data.length - currentPos);
                    System.arraycopy(b, off, data, currentPos, toWrite);
                    currentPos += toWrite;
                    len -= toWrite;
                    off += toWrite;
                } else {
                    MessageInfo.toBytes(isFirst ? MessageType.START : MessageType.CONTENT, id, currentPos - MessageInfo.SIZE, data);
                    outputStream.write(data, 0, currentPos);
                    isFirst = false;
                    currentPos = MessageInfo.SIZE;
                }
            }
        }

        public void write(int b) throws IOException {
            if (currentPos < data.length) {
                data[currentPos++] = (byte) b;
            } else {
                startNew((byte) b);
            }
        }

        private void startNew(byte b) throws IOException {
            MessageInfo.toBytes(isFirst ? MessageType.START : MessageType.CONTENT, id, currentPos - MessageInfo.SIZE, data);
            outputStream.write(data, 0, currentPos);
            isFirst = false;
            currentPos = MessageInfo.SIZE;
            data[currentPos++] = b;
        }

        public void end() throws IOException {
            MessageInfo.toBytes(isFirst ? MessageType.START_END : MessageType.END, id, currentPos - MessageInfo.SIZE, data);
            outputStream.write(data, 0, currentPos);
            pendingWriteBuffers.put(data);
            isFirst = true;
            data = EMPTY_DATA;
        }

        public void close() throws IOException {
            log.info("Socket closed.");
            outputStream.close();
            if (data != EMPTY_DATA) {
                String s = "Close call but still data";
                log.error(s, new RuntimeException(s));
                // we release the buffer without adding it to
                data = EMPTY_DATA;
            }
        }

        public void flush() throws IOException {
            if (currentPos != MessageInfo.SIZE) {
                MessageInfo.toBytes(isFirst ? MessageType.START : MessageType.CONTENT, id, currentPos - MessageInfo.SIZE, data);
                outputStream.write(data, 0, currentPos);
                currentPos = MessageInfo.SIZE;
                isFirst = false;
            }
        }

        void reset(int id) {
            if (data != EMPTY_DATA) {
                String s = "End not call.";
                RuntimeException runtimeException = new RuntimeException(s);
                log.error(s, runtimeException);
                throw runtimeException;
            }
            this.id = id;
            isFirst = true;
            currentPos = MessageInfo.SIZE;
            data = pendingWriteBuffers.take();
        }
    }

    private static class DirectClientRequestFactory implements ClientRequestFactory {
        private final String url;
        private final String host;
        private final int port;
        private AtomicInteger atomicInteger = new AtomicInteger(0);
        private BlockingQueue<DirectClientRequest> directClientRequests = new ArrayBlockingQueue<>(CACHE_MAX_OF_CLIENT_REQUEST); // FIFO for check
        private Set<DirectClientRequest> activeClients = new HashSet<>(); // to prevent gc of DirectClientRequest

        public DirectClientRequestFactory(String url) {
            this.url = url;
            if (!url.startsWith("tcp://")) {
                throw new RuntimeException("Only tcp:// url are managed. Got '" + url + "'");
            }
            String subUrl = url.substring("tcp://".length());
            int i = subUrl.indexOf(":");
            if (i == -1) {
                throw new RuntimeException("tcp://host:port expected. Got '" + url + "' (" + subUrl + ")");
            }
            host = subUrl.substring(0, i);
            port = Integer.parseInt(subUrl.substring(i + 1));
        }

        public PeerToPeer.ClientRequest create() {
            try {
                DirectClientRequest directClientRequest = directClientRequests.poll();
                if (directClientRequest == null) {
                    final Socket socket = new Socket(host, port);
                    socket.setTcpNoDelay(TCP_NO_DELAY);
                    directClientRequest = new DirectClientRequest(socket);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Use " + directClientRequest.socketUrl);
                    }
                }
                activeClients.add(directClientRequest);
                directClientRequest.reset(atomicInteger.incrementAndGet());
                return new UserClientRequest(this, directClientRequest);
            } catch (IOException e) {
                throw new RuntimeException("Can not connect to '" + url + "'", e);
            }
        }

        public void release() {
            for (DirectClientRequest directClientRequest : directClientRequests) {
                directClientRequest.end();
            }
            directClientRequests.clear();
            activeClients.clear();
        }

        public boolean check() {
            long past = System.currentTimeMillis() - GRACE_PERIOD;
            int size = directClientRequests.size();
            if (size > CACHE_OF_CLIENT_REQUEST) {
                for (int i = 0; i < size; i++) {
                    DirectClientRequest clientRequest = directClientRequests.poll();
                    if (clientRequest == null) {
                        break;
                    }
                    if (past > clientRequest.lastRelease || !directClientRequests.offer(clientRequest)) {
                        log.info("Release from check " + clientRequest.socketUrl);
                        clientRequest.end();
                    }
                    if (directClientRequests.size() <= CACHE_OF_CLIENT_REQUEST) {
                        break;
                    }
                }
            }
            DirectClientRequest clientRequest = directClientRequests.poll();
            if (clientRequest != null) {
                if (clientRequest.ping()) {
                    if (!directClientRequests.offer(clientRequest)) {
                        if (log.isInfoEnabled()) {
                            log.info("cache full, releasing " + clientRequest.socketUrl);
                        }
                        clientRequest.end();
                    }
                    return true;
                } else {
                    return false;
                }
            }
            return true;
        }

        private boolean release(DirectClientRequest directClientRequest) {
            directClientRequest.lastRelease = System.currentTimeMillis();
            if (log.isDebugEnabled()) {
                log.debug("Release " + directClientRequest.socketUrl);
            }
            activeClients.remove(directClientRequest);
            if (!directClientRequests.offer(directClientRequest)) {
                if (log.isInfoEnabled()) {
                    log.info("Force end for " + directClientRequest.socketUrl);
                }
                directClientRequest.end();
            }
            return false;
        }

        private void clear(DirectClientRequest directClientRequest) {
            if (log.isDebugEnabled()) {
                log.debug("Clear " + directClientRequest.socketUrl);
            }
            activeClients.remove(directClientRequest);
            directClientRequest.end();
        }

        private static class DirectClientRequest implements PeerToPeer.ClientRequest {
            private static AtomicInteger count = new AtomicInteger(1);
            private final Socket socket;
            private final String socketUrl;
            ChunckedOutputStream chunckedOutputStream;
            ChunckInputStream chunckedInputStream;
            private int callId;
            private long lastRelease = 0;

            public DirectClientRequest(Socket socket) throws IOException {
                this.socket = socket;
                socketUrl = this.socket.toString() + " (" + count.incrementAndGet() + ")";
                chunckedOutputStream = new ChunckedOutputStream(trace(socket.getOutputStream(), socketUrl));
                chunckedInputStream = new ChunckInputStream(trace(socket.getInputStream(), socketUrl));
                log.info("New connection to " + socketUrl);
            }

            public OutputStream getRequestStream() {
                return chunckedOutputStream;
            }

            public InputStream getResponseInputStream() {
                return chunckedInputStream;
            }

            public boolean ping() {
                try {
                    chunckedOutputStream.ping();
                    if (!chunckedInputStream.nextCall()) {
                        return false;
                    }
                    if (!chunckedInputStream.isPing()) {
                        return false;
                    }
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }

            public void requestComplete() {
                try {
                    chunckedOutputStream.end();
                } catch (IOException e) {
                    String message = "Fail to flush data for " + socketUrl;
                    log.error(message, e);
                    throw new RuntimeException(message, e);
                }
            }

            public void end() {
                try {
                    log.info("end: close socket " + socketUrl);
                    chunckedOutputStream.close();
                    chunckedInputStream.close();
                    socket.close();
                } catch (IOException e) {
                    String message = "Fail to flush data";
                    log.error(message, e);
                    throw new RuntimeException(message, e);
                }
            }

            public void reset(int callId) {
                this.callId = callId;
                chunckedOutputStream.reset(callId);
                chunckedInputStream.init();
            }
        }

        private static class UserClientRequest implements ClientRequest {
            private final DirectClientRequestFactory directClientRequestFactory;
            DirectClientRequest directClientRequest;
            UserOutputStream userOutputStream;
            UserInputStream userInputStream;

            private UserClientRequest(DirectClientRequestFactory directClientRequestFactory, DirectClientRequest directClientRequest) {
                this.directClientRequestFactory = directClientRequestFactory;
                this.directClientRequest = directClientRequest;
                userOutputStream = new UserOutputStream(directClientRequest.chunckedOutputStream, directClientRequest.socketUrl, this);
            }

            public OutputStream getRequestStream() {
                return userOutputStream;
            }

            public InputStream getResponseInputStream() {
                if (userInputStream == null) {
                    userInputStream = new UserInputStream(directClientRequest.chunckedInputStream, directClientRequest.socketUrl, this);
                }
                return userInputStream;
            }

            public void requestComplete() {
                try {
                    if (userOutputStream != null) {
                        try {
                            userOutputStream.close();
                        } finally {
                            userOutputStream = null;
                        }
                    }
                    getResponseInputStream(); // to force the userInputStream to be available so the peer will received at least the end.
                } catch (IOException e) {
                    String message = "Fail to flush data for " + directClientRequest.socketUrl;
                    log.error(message, e);
                    throw new RuntimeException(message, e);
                }
            }

            public void end() {
                if (directClientRequest != null) {
                    try {
                        requestComplete();  // call also here to be sure that in an exception was thrown and it was not called it will be called
                        // and if the socket is closed an exception is raised again.
                        if (userInputStream != null && userInputStream.inputStream != null) {
                            if (!userInputStream.isFullyRead()) {
                                throw new RuntimeException("Bug : end call but input stream not fully read " + directClientRequest.socketUrl);
                            }
                        }
                        directClientRequestFactory.release(directClientRequest);
                    } catch (Exception e) {
                        directClientRequestFactory.clear(directClientRequest);
                        throw new RuntimeException("Exception while ending request", e);
                    } finally {
                        directClientRequest = null;
                    }
                }
            }

            // here we no call end any more.
            protected void finalize() throws Throwable {
                if (directClientRequest != null) {
                    log.error("End was not call for " + directClientRequest.socketUrl);
                }
                super.finalize();
            }

            public String toString() {
                if (directClientRequest != null) {
                    return directClientRequest.socketUrl;
                }
                return super.toString();
            }
        }
    }

    static class PendingBuffers {
        ArrayBlockingQueue<byte[]> bytes = new ArrayBlockingQueue<>(MAX_BUFFER_COUNT);
        private int bufferSize;

        public PendingBuffers(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public byte[] take() {
            byte[] poll = bytes.poll();
            if (poll == null) {
                return new byte[bufferSize];
            }
            return poll;
        }

        public void put(byte[] data) {
            if (data.length != 0) {
                bytes.offer(data);
            }
        }
    }

    public static class NullInputStream extends InputStream {
        public static InputStream INSTANCE = new NullInputStream();

        public int read() throws IOException {
            throw new IOException("End of file");
        }
    }

    class ConnectionChecker extends TimerTask {

        public void run() {
            try {
                for (Map.Entry<String, DirectClientRequestFactory> stringClientRequestFactoryEntry : clients.entrySet()) {
                    DirectClientRequestFactory requestFactory = stringClientRequestFactoryEntry.getValue();
                    if (!requestFactory.check()) {
                        log.info("Releasing " + stringClientRequestFactoryEntry.getKey());
                        clients.remove(stringClientRequestFactoryEntry.getKey());
                        requestFactory.release();
                    }
                }
            } catch (Exception e) {
                log.error("During check", e);
            }
        }
    }

}

