package org.globsframework.remote.rpc;

import org.globsframework.remote.Serializer;

import java.io.Writer;
import java.lang.reflect.Type;
import java.util.List;

public interface RpcService {

    <Service>
    Service getService(Class<Service> serviceClass, String key, Serializer... serializers);

    <Service>
    Service getTemporaryService(Class<Service> serviceClass, String key, Serializer... serializers);

    <Service>
    void register(Class<Service> serviceClass, Service service, String key, Serializer... serializers);

    <T>
    void addSerializer(Serializer<T> serializer);

    void addSerializerAccessor(SerializerAccessor serializerAccessor);

    <T>
    List<String> listService(Class<T> serviceClass);

    <Service>
    void unregister(Class<Service> serviceClass, String name);

    void reset();

    void dumpRegistered(Writer writer);


    interface SerializerAccessor {
        Serializer getSerializer(Type aClass);
    }

}
