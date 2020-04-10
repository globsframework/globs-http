package org.globsframework.remote.shared.impl;

import org.globsframework.directory.Cleanable;
import org.globsframework.directory.Directory;
import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.model.repository.DefaultGlobRepository;
import org.globsframework.model.utils.GlobFunctor;
import org.globsframework.remote.shared.ServerSharedData;
import org.globsframework.utils.ReusableByteArrayOutputStream;
import org.globsframework.utils.collections.IntHashMap;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedInputOutputFactory;
import org.globsframework.utils.serialization.SerializedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * format is :
 * int : size
 * int : changeId (0 => initial empty msg, changeId > 0 => changeSet, changeId < 0 => repository
 * int : connectionId (0 serveur)
 * Payload
 */

public class DefaultServerSharedData implements ChangeSetListener, ServerSharedData, Cleanable {
    public static final boolean TCP_NO_DELAY = Boolean.parseBoolean(System.getProperty("org.globsframework.shared.setTcpNoDelay", "true"));
    public static final int MAX_MSG_TO_READ = 10;
    static private Logger logger = LoggerFactory.getLogger(DefaultServerSharedData.class);
    private final ServerSocketChannel serverSocketChannel;
    private final String name;
    IntHashMap<RemoteRWState> stateMaps = new IntHashMap<>();
    private int currentChange = 1;
    private Selector selector;
    private DefaultGlobModel globModel;
    private String host;
    private GlobRepository globRepository;
    private ReusableByteArrayOutputStream outputStream;
    private byte[] currentRepo = null;
    private int currentRepoLen = 0;
    private Thread thread;
    private SharedModelType sharedModelType;
    private Random random = new Random();
    private boolean stopped = false;

    public DefaultServerSharedData(GlobModel globModel, String name) {
        this(globModel, getLocalHost(), 0, name);
    }

    public DefaultServerSharedData(GlobModel globModel, final String host, int port, String name) {
        try {
            this.name = name;
            this.globModel = new DefaultGlobModel(globModel);
            this.host = host;
            sharedModelType = new SharedModelType(globModel);
            this.selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            ServerSocket socket = serverSocketChannel.socket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            globRepository = new DefaultGlobRepository();
            globRepository.addChangeListener(this);
            outputStream = new ReusableByteArrayOutputStream();
            thread = new ServerSharedDataThread(this);
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            serverSocketChannel.close();
        } catch (Exception e) {
            logger.error("in close serverSocketChannel", e);
        }
        try {
            selector.close();
        } catch (Exception e) {
            logger.error("in close selector", e);
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            logger.error("in thread join", e);
        }
        for (RemoteRWState stateMap : stateMaps.values()) {
            try {
                stateMap.cancel();
            } catch (Exception e) {
                logger.error("in cancel statemap", e);
            }
        }
    }

    public int getPort() {
        return serverSocketChannel.socket().getLocalPort();
    }

    public String getHost() {
        return host;
    }

    public void addGlobTypes(GlobType... globTypes) {
        Arrays.stream(globTypes).forEach(globModel::add);
    }

    public void run() throws Exception {
        SelectionKey serverSocketKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<RemoteRWState> clientToRemove = new HashSet<>();
        while (true) {
            int select = selector.select();
            if (select > 0) {
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                if (selectionKeys.contains(serverSocketKey)) {
                    logger.info("New client");
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    if (socketChannel != null) {
                        socketChannel.socket().setTcpNoDelay(TCP_NO_DELAY);
                        SelectionKey selectionKey = null;
                        try {
                            socketChannel.configureBlocking(false);
                            int clientId = getNextId();
                            RemoteRWState remoteState = new RemoteRWState(socketChannel);
                            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ, remoteState);
                            remoteState.setSelectionKey(selectionKey);
                            remoteState.setClientId(clientId);
                            {
                                ByteArrayOutputStream repoOutputStream = new ByteArrayOutputStream();
                                final SerializedOutput serializedOutput = SerializedInputOutputFactory.initCompressed(repoOutputStream);
                                serializedOutput.write(ClientSharedData.getSizeWithKey(8));
                                serializedOutput.write(0);
                                serializedOutput.write(clientId);
                                remoteState.write(repoOutputStream.toByteArray(), 12);
                            }
                            if (currentRepo == null) {
                                ReusableByteArrayOutputStream repoOutputStream = new ReusableByteArrayOutputStream();
                                repoOutputStream.setTo(4);
                                final SerializedOutput serializedOutput = SerializedInputOutputFactory.initCompressed(repoOutputStream);
                                serializedOutput.write(-currentChange);
                                serializedOutput.write(0);
                                int globCount = globRepository.size();
                                serializedOutput.write(globCount);
                                globRepository.safeApply(new GlobFunctor() {
                                    public void run(Glob glob, GlobRepository repository) throws Exception {
                                        serializedOutput.writeGlob(glob);
                                    }
                                });
                                currentRepoLen = repoOutputStream.size();
                                repoOutputStream.reset();
                                serializedOutput.write(ClientSharedData.getSizeWithKey(currentRepoLen - 4));
                                currentRepo = repoOutputStream.getBuffer();
                            }
                            remoteState.write(currentRepo, currentRepoLen);
                            stateMaps.put(clientId, remoteState);
                            logger.info("Send repo to " + clientId);
                        } catch (IOException e) {
                            logger.info("fail to open connection to client");
                            if (selectionKey != null) {
                                selectionKey.cancel();
                            }
                            if (socketChannel != null) {
                                try {
                                    socketChannel.close();
                                } catch (IOException e1) {
                                    logger.info("Fail to close connection.");
                                }
                            }
                        }
                    }
                    selectionKeys.remove(serverSocketKey);
                }

                if (!selectionKeys.isEmpty()) {
                    for (SelectionKey selectionKey : selectionKeys) {
                        Object attachment = selectionKey.attachment();
                        if (attachment != null) {
                            RemoteRWState remoteState = (RemoteRWState) attachment;
                            try {
                                if (selectionKey.isValid() && selectionKey.isReadable()) {
                                    globRepository.startChangeSet();
                                    try {
                                        remoteState.read(new RemoteRWState.MsgCallback() {
                                            public void msg(byte[] msg, int msgSize) {
                                                SerializedInput serializedInput = SerializedInputOutputFactory.initCompressedAndIntern(msg, msgSize);
                                                int changeId = serializedInput.readNotNullInt();
                                                int connectionId = serializedInput.readNotNullInt();
                                                if (changeId > 0) {
                                                    if (logger.isDebugEnabled()) {
                                                        logger.debug("Receive changeSet " + connectionId + ":" + changeId);
                                                    }
                                                    ChangeSet changeSet = serializedInput.readChangeSet(globModel);
                                                    globRepository.apply(changeSet);
                                                }
                                                if (changeId < 0) {
                                                    int len = serializedInput.readNotNullInt();
                                                    if (logger.isInfoEnabled()) {
                                                        logger.info("Receive repo with " + len + " globs " + connectionId + ":" + changeId);
                                                    }
                                                    GlobList globs = new GlobList(len);
                                                    while (len > 0) {
                                                        globs.add(serializedInput.readGlob(globModel));
                                                        len--;
                                                    }
                                                    for (Glob glob : globs) {
                                                        globRepository.create(glob);
                                                    }
                                                }
                                            }
                                        }, MAX_MSG_TO_READ);
                                    } finally {
                                        globRepository.completeChangeSet();
                                    }
                                    sendToAllClient(remoteState, clientToRemove);
                                }
                                if (selectionKey.isValid() && selectionKey.isWritable()) {
                                    remoteState.writeNext();
                                }
                            } catch (IOException e) {
                                logger.info("Client closed during io " + e.getMessage());
                                remoteState.cancel();
                            }
                            if (remoteState.isClosed() || !selectionKey.isValid()) {
                                logger.info("Client closed");
                                clientToRemove.add(remoteState);
                            }
                        }
                    }
                }
                selectionKeys.clear();
            }

            while (!clientToRemove.isEmpty()) {
                globRepository.startChangeSet();
                try {
                    for (RemoteRWState client : clientToRemove) {
                        logger.info("Send delete for " + client.getClientId());
                        client.cancel();
                        removeClient(client);
                        sharedModelType.cleanThisId(globRepository, client.getClientId());
                    }
                } finally {
                    globRepository.completeChangeSet();
                }
                clientToRemove.clear();
                sendToAllClient(null, clientToRemove);
            }
        }
    }

    // remove ++connectionId; and use random to reduce hashCode collision
    private int getNextId() {
        int id = random.nextInt();
        while (stateMaps.containsKey(id)) {
            id = random.nextInt();
        }
        return id;
    }

    private void removeClient(RemoteRWState remoteState) {
        if (stateMaps.remove(remoteState.getClientId()) == null) {
            logger.error(remoteState.getClientId() + " to remove not found");
        }
    }

    private void sendToAllClient(RemoteRWState remoteState, Set<RemoteRWState> clientToRemove) {
        if (outputStream.size() > 0) {
            boolean isFullySent = true;
            for (RemoteRWState rem : stateMaps.values()) {
                if (rem != remoteState) {
                    boolean fullySent = true;
                    try {
                        fullySent = rem.write(outputStream.getBuffer(), outputStream.size());
                    } catch (IOException e) {
                        clientToRemove.add(rem);
                    }
                    isFullySent &= fullySent;
                }
            }
            if (isFullySent) {
                outputStream.reset();
            } else {
                outputStream = new ReusableByteArrayOutputStream();
            }
        }
    }

    public void globsChanged(ChangeSet changeSet, GlobRepository repository) {
        if (logger.isTraceEnabled()) {
            logger.trace("change set : " + changeSet.toString());
        }
        currentRepo = null;
        if (!changeSet.isEmpty()) {
            outputStream.setTo(4);
            SerializedOutput serializedOutput = SerializedInputOutputFactory.initCompressed(outputStream);
            serializedOutput.write(++currentChange);
            serializedOutput.write(0);
            serializedOutput.writeChangeSet(changeSet);
            int size = outputStream.size();
            outputStream.reset();
            if (logger.isDebugEnabled()) {
                logger.debug("Send changeSet " + 0 + ":" + currentChange);
            }
            serializedOutput.write(ClientSharedData.getSizeWithKey(size - 4));
            outputStream.setTo(size);
        }
    }

    public void globsReset(GlobRepository repository, Set<GlobType> changedTypes) {
    }

    public void clean(Directory directory) {
        stop();
    }

    private static class ServerSharedDataThread extends Thread {
        private final DefaultServerSharedData defaultServerSharedData;

        public ServerSharedDataThread(DefaultServerSharedData defaultServerSharedData) {
            super("ServerSharedData '" + defaultServerSharedData.name + "' on port " + defaultServerSharedData.getPort());
            this.defaultServerSharedData = defaultServerSharedData;
            setPriority(MAX_PRIORITY - 1);
        }

        public void run() {
            try {
                logger.info("starting shared data server on " + defaultServerSharedData.host + ":" + defaultServerSharedData.getPort());
                defaultServerSharedData.run();
                logger.info("Shared Data Server end");
            } catch (Exception e) {
                if (!(e instanceof IOException) && !(e instanceof ClosedSelectorException)) {
                    logger.error("Shared Data Server end", e);
                } else {
                    logger.warn("Connection closed : " + e.getMessage());
                }
            }
        }
    }
}
