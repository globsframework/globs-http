package org.globsframework.remote;

import org.globsframework.remote.request.Shared;
import org.globsframework.remote.rpc.ExportMethod;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.utils.collections.LongHashMap;
import org.globsframework.utils.serialization.SerializedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

public class DefaultSharedService implements SharedService {
    public static final String DEFAULT = "default";
    private static Logger logger = LoggerFactory.getLogger(DefaultSharedService.class);
    protected final int serverId;
    Map<Object, Shared> sharedMap = new IdentityHashMap<>();
    LongHashMap<Shared> shareds = new LongHashMap<>();
    private int objectId = 0;
    private int currentLocalId;

    public DefaultSharedService(RpcService rpcService) {
        this(rpcService.getTemporaryService(RpcSharedService.class, DEFAULT)
                .createId());
    }

    public DefaultSharedService(int serverId) {
        this.serverId = serverId;
    }

    public static void initUniqueId(RpcService rpcService) {
        int serverId = SystemUtils.createIdFromCurrentTimeMillis();
        logger.info("start serverId at " + serverId);
        rpcService.register(RpcSharedService.class, new UniqueIdRpcSharedService(serverId), DEFAULT);
    }

    public synchronized <T> Shared<T> declare(T value) {
        Shared globallyShared = sharedMap.get(value);
        if (globallyShared != null) {
            return globallyShared;
        }
        int currentId = ++objectId;
        globallyShared = new DefaultGloballyShared<>(value, serverId, currentId);
        sharedMap.put(value, globallyShared);
        long id = serverId;
        id = id << 32;
        id |= currentId;
        shareds.put(id, globallyShared);
        return globallyShared;
    }

    public <T> Shared<T> restoreShared(SerializedInput input, T t) {
        int serverId = input.readNotNullInt();
        int objectId = input.readNotNullInt();
        long id = serverId;
        id = id << 32;
        id |= objectId;
        synchronized (this) {
            Shared globallyShared = shareds.get(id);
            if (globallyShared == null) {
                if (serverId == this.serverId) {
                    throw new RuntimeException(t + " should be local (" + serverId + ":" + objectId + ") but is not");
                }
                globallyShared = new DefaultGloballyShared<>(t, serverId, objectId);
                shareds.put(id, globallyShared);
            }
            return globallyShared;
        }
    }

    public void reset() {
        sharedMap.clear();
        shareds.clear();
    }

    public long newUniqueId() {
        long id = serverId;
        id = id << 32;
        synchronized (this) {
            id |= (++currentLocalId);
            return id;
        }
    }

    public synchronized Shared get(long id) {
        return shareds.get(id);
    }

    public interface RpcSharedService {
        @ExportMethod
        int createId();
    }

    public static class UniqueIdRpcSharedService implements RpcSharedService {
        private int serverId;

        public UniqueIdRpcSharedService(int serverId) {
            this.serverId = serverId;
        }

        @ExportMethod
        public int createId() {
            int id;
            synchronized (this) {
                id = ++serverId;
            }
            logger.info("new serverId : " + id);
            return id;
        }
    }
}
