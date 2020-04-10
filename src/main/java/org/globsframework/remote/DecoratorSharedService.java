package org.globsframework.remote;

import org.globsframework.remote.request.Shared;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.utils.serialization.SerializedInput;

public class DecoratorSharedService extends DefaultSharedService {
    private DefaultSharedService parentSharedService;

    public DecoratorSharedService(RpcService rpcService, SharedService parentShared) {
        super(rpcService);
        this.parentSharedService = (DefaultSharedService) parentShared;
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
                globallyShared = parentSharedService.get(id);
                if (globallyShared == null) {
                    if (serverId == this.serverId) {
                        throw new RuntimeException(t + " should be local (" + serverId + ":" + objectId + ") but is not");
                    }
                    globallyShared = new DefaultGloballyShared<>(t, serverId, objectId);
                    shareds.put(id, globallyShared);
                }
            }
            return globallyShared;
        }
    }
}
