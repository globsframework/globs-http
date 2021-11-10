package org.globsframework.remote.rpc.impl;

import org.globsframework.directory.Cleanable;
import org.globsframework.directory.Directory;
import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.impl.DefaultGlobModel;
import org.globsframework.model.*;
import org.globsframework.model.format.GlobPrinter;
import org.globsframework.model.utils.GlobMatchers;
import org.globsframework.remote.DirectoryProvider;
import org.globsframework.remote.Serializer;
import org.globsframework.remote.SharedService;
import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.ServerListener;
import org.globsframework.remote.request.Shared;
import org.globsframework.remote.rpc.ExportMethod;
import org.globsframework.remote.rpc.RpcService;
import org.globsframework.remote.shared.SharedDataManager;
import org.globsframework.remote.shared.SharedDataService;
import org.globsframework.remote.shared.impl.SharedPathBuilder;
import org.globsframework.utils.Ref;
import org.globsframework.utils.Utils;
import org.globsframework.utils.collections.MapOfMaps;
import org.globsframework.utils.collections.MultiMap;
import org.globsframework.utils.collections.Pair;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedInputOutputFactory;
import org.globsframework.utils.serialization.SerializedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.*;

public class DefaultRpcService implements RpcService, Cleanable {
    public static final int MAX_WAIT_RPC_PROXY = Integer.getInteger("org.globsframework.remote.rpc.wait", 15000);
    public static final String SERVICES = "/services";
    private static Logger logger = LoggerFactory.getLogger(DefaultRpcService.class);
    final MapOfMaps<String, String, LocalCall> services = new MapOfMaps<>();
    final MapOfMaps<Class, String, SoftReference<Object>> proxy = new MapOfMaps<>();
    private final String workerName;
    Map<Class, Serializer> serializers = new HashMap<>();
    SharedDataService sharedDataService;
    PeerToPeer peerToPeer;
    ServerListener serverListener;
    SerializerProvider defaultSerializerProvider;
    private DirectoryProvider directoryProvider;
    private List<SerializerAccessor> serializerAccessors = new ArrayList<>();

    public DefaultRpcService(String workerName, DirectoryProvider directoryProvider, SharedDataManager sharedDataManager, PeerToPeer peerToPeer) {
        this(0, workerName, directoryProvider, sharedDataManager, peerToPeer);
    }

    public DefaultRpcService(int port, String workerName, DirectoryProvider directoryProvider, SharedDataManager sharedDataManager, PeerToPeer peerToPeer) {
        this.workerName = workerName;
        this.directoryProvider = directoryProvider;
        GlobModel globModel = new DefaultGlobModel(ServiceType.TYPE);
        sharedDataService = sharedDataManager.getSharedDataServiceSync(SharedPathBuilder.create(SERVICES), globModel);
        this.peerToPeer = peerToPeer;
        serverListener = this.peerToPeer.createServerListener(port, 1, new PeerToPeer.ServerRequestFactory() {
            public PeerToPeer.ServerRequestProcessor createServerRequest() {
                return new PeerToPeer.ServerRequestProcessor() {
                    public void receive(PeerToPeer.ServerRequest serverRequest, PeerToPeer.ServerResponseBuilder response) {
                        InputStream requestStream = serverRequest.getRequestStream();
                        SerializedInput serializedInput = SerializedInputOutputFactory.init(requestStream);
                        String className = serializedInput.readUtf8String();
                        String key = serializedInput.readUtf8String();
                        LocalCall localCall;
                        synchronized (services) {
                            localCall = services.get(className, key);
                        }
                        if (localCall == null) {
                            throw new RuntimeException("Unknown service " + className + " : " + key);
                        }
                        localCall.call(serializedInput, SerializedInputOutputFactory.init(response.getResponseStream()));
                        response.complete();
                    }
                };
            }
        }, "RpcService");
        defaultSerializerProvider = new DefaultSerializerProvider(this, directoryProvider);
        logger.info("Rpc listener on " + serverListener.getUrl());
    }

    public static void registerRpcNamingServiceHere(SharedDataManager sharedDataManager) {
        logger.info("Init rpc naming service server.");
        sharedDataManager.create(SharedPathBuilder.create(SERVICES), new DefaultGlobModel(ServiceType.TYPE));
    }

    public static void registerRpcNamingServiceHere(SharedDataManager sharedDataManager, int port) {
        logger.info("Init rpc naming service server.");
        sharedDataManager.create(SharedPathBuilder.create(SERVICES), new DefaultGlobModel(ServiceType.TYPE), port);
    }

    private static void writeThrowable(Throwable e, SerializedOutput serializedOutput) {
        serializedOutput.writeString(e.getMessage());
        serializedOutput.writeString(e.toString());
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace == null) {
            serializedOutput.writeInteger(-1);
            return;
        }
        serializedOutput.writeInteger(stackTrace.length);
        for (StackTraceElement stackTraceElement : stackTrace) {
            serializedOutput.writeString(stackTraceElement.getClassName());
            serializedOutput.writeString(stackTraceElement.getMethodName());
            serializedOutput.writeString(stackTraceElement.getFileName());
            serializedOutput.writeInteger(stackTraceElement.getLineNumber());
        }
        Throwable cause = e.getCause();
        boolean hasCausedBy = cause != null && cause != e;
        serializedOutput.writeBoolean(hasCausedBy);
        if (hasCausedBy) {
            writeThrowable(cause, serializedOutput);
        }
    }

    private static Throwable readThrowable(SerializedInput serializedInput) {
        String message = serializedInput.readString();
        String toString = serializedInput.readString();
        RemoteThrowable res = new RemoteThrowable(message, toString);
        int stackTraceLenght = serializedInput.readInteger();
        if (stackTraceLenght != -1) {
            StackTraceElement[] stackTrace = new StackTraceElement[stackTraceLenght];
            for (int i = 0; i < stackTraceLenght; i++) {
                stackTrace[i] = new StackTraceElement(
                        serializedInput.readString(), // Class
                        serializedInput.readString(), // Method
                        serializedInput.readString(), // FileName
                        serializedInput.readInteger() // Line
                );
            }
            res.setStackTrace(stackTrace);
        }
        boolean hasCausedBy = serializedInput.readBoolean();
        if (hasCausedBy) {
            res.initCause(readThrowable(serializedInput));
        }
        return res;
    }

    private static ArgRW createArgRW(SerializerProvider serializerProvider, Type parameterType) {
        if (parameterType.equals(Void.TYPE)) {
            return new VoidArgRW();
        } else if (parameterType.equals(Integer.class) || parameterType.equals(Integer.TYPE)) {
            return new IntArgRW((Class<?>) parameterType);
        } else if (parameterType.equals(Long.class) || parameterType.equals(Long.TYPE)) {
            return new LongArgRW((Class<?>) parameterType);
        } else if (parameterType.equals(Boolean.class) || parameterType.equals(Boolean.TYPE)) {
            return new BooleanArgRW((Class<?>) parameterType);
        } else if (parameterType.equals(Byte.class) || parameterType.equals(Byte.TYPE)) {
            return new ByteArgRW((Class<?>) parameterType);
        } else if (parameterType.equals(Character.class) || parameterType.equals(Character.TYPE)) {
            return new CharacterArgRW((Class<?>) parameterType);
        } else if (parameterType.equals(String.class)) {
            return new StringArgRW((Class<?>) parameterType);
        } else if ((parameterType instanceof Class) && ((Class) parameterType).isArray()) {
            Class<?> componentType = ((Class) parameterType).getComponentType();
            if (componentType.equals(Byte.TYPE)) {
                return new ByteArrayArgRW();
            } else if (componentType.equals(Integer.TYPE)) {
                return new IntArrayArgRW();
            } else if (componentType.equals(Long.TYPE)) {
                return new LongArrayArgRW();
            } else if (componentType.equals(Double.TYPE)) {
                return new DoubleArrayArgRW();
            } else {
                return new ArrayArgRW(componentType, createArgRW(serializerProvider, componentType));
            }
        } else if (parameterType instanceof GenericArrayType) {
            Class<?> componentType = (Class<?>) ((GenericArrayType) parameterType).getGenericComponentType();
            if (componentType.equals(Byte.TYPE)) {
                return new ByteArrayArgRW();
            } else if (componentType.equals(Integer.TYPE)) {
                return new IntArrayArgRW();
            } else if (componentType.equals(Long.TYPE)) {
                return new LongArrayArgRW();
            } else if (componentType.equals(Double.TYPE)) {
                return new DoubleArrayArgRW();
            } else {
                return new ArrayArgRW(componentType, createArgRW(serializerProvider, componentType));
            }
        } else if (parameterType instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) parameterType;
            if (parameterizedType.getRawType().equals(Ref.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                return new RefArgRW(parameterizedType.getActualTypeArguments()[0], argRW1);
            } else if (parameterizedType.getRawType().equals(List.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                return new ListArgRW(parameterizedType.getActualTypeArguments()[0], argRW1);
            } else if (parameterizedType.getRawType().equals(Map.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                final ArgRW argRW2 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[1]);
                return new MapArgRW(parameterizedType.getActualTypeArguments()[0], argRW1, parameterizedType.getActualTypeArguments()[1], argRW2);
            } else if (parameterizedType.getRawType().equals(MultiMap.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                final ArgRW argRW2 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[1]);
                return new MultiMapArgRW(parameterizedType.getActualTypeArguments()[0], argRW1, parameterizedType.getActualTypeArguments()[1], argRW2);
            } else if (parameterizedType.getRawType().equals(Pair.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                final ArgRW argRW2 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[1]);
                return new PairArgRW(parameterizedType.getActualTypeArguments()[0], argRW1, parameterizedType.getActualTypeArguments()[1], argRW2);
            } else if (parameterizedType.getRawType().equals(Shared.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                return new GloballySharedArgRW(serializerProvider, parameterizedType, argRW1);
            } else if (parameterizedType.getRawType().equals(Set.class)) {
                final ArgRW argRW1 = createArgRW(serializerProvider, parameterizedType.getActualTypeArguments()[0]);
                return new SetArgRW(parameterizedType.getActualTypeArguments()[0], argRW1);
            }
        }
        if (parameterType instanceof Class<?>) {
            final Serializer serializer = serializerProvider.get(((Class) parameterType));
            if (serializer != null) {
                return new FromSerializerNamedArg(parameterType, serializer, serializerProvider);
            }
//            if (PropertyContext.class.isAssignableFrom((Class) parameterType)) {
//                return new PropertyContextArg(serializerProvider);
//            }
            if (Serializable.class.isAssignableFrom((Class) parameterType)) {
                return new SerializableArg((Class) parameterType);
            }
        }
        throw new RuntimeException("Serializer not found for " + parameterType.toString());
    }

    public <Service> Service getService(final Class<Service> serviceClass, final String key, Serializer... serializers) {
        return getService(serviceClass, true, key, serializers);
    }

    public <Service> Service getTemporaryService(final Class<Service> serviceClass, final String key, Serializer... serializers) {
        return getService(serviceClass, false, key, serializers);
    }

    public <Service> Service getService(final Class<Service> serviceClass, boolean shouldListen, final String key, Serializer... serializers) {
        Service service = null;
        final boolean isStateless = (serializers == null || serializers.length == 0);
        if (isStateless) {
            SoftReference<Object> objectSoftReference;
            synchronized (proxy) {
                objectSoftReference = proxy.get(serviceClass, key);
            }
            if (objectSoftReference != null) {
                service = (Service) objectSoftReference.get();
            }
        }
        if (service == null) {
            service = (Service) Proxy.newProxyInstance(serviceClass.getClassLoader(),
                    new Class[]{serviceClass},
                    new PreloadedInvocationHandler(workerName, serviceClass, key, shouldListen, sharedDataService, peerToPeer, getSerializer(directoryProvider, serializers)));
            if (isStateless && shouldListen) {
                synchronized (proxy) {
                    proxy.put(serviceClass, key, new SoftReference<Object>(service));
                }
            }
        }
        return service;
    }

    public <Service> void register(final Class<Service> serviceClass, Service service, String key, Serializer... serializers) {
//        Directory localDirectory = new DefaultDirectory(this.directory);
//        localDirectory.add(SharedService.class, sharedService);
        Method[] methods = serviceClass.getMethods();
        LocalCall localCall = new LocalCall(service);
        for (Method method : methods) {
            if (method.getAnnotation(ExportMethod.class) != null) {
                Method implMethod = null;
                Class<?> aClass = service.getClass();
                while (implMethod == null) {
                    try {
                        implMethod = aClass.getMethod(method.getName(), method.getParameterTypes());
                    } catch (NoSuchMethodException e) {
                        aClass = getParentClass(aClass);
                        if (aClass == null) {
                            throw new RuntimeException("Can not find " + method + " implementation in " + service.getClass(), e);
                        }
                    }
                }
                localCall.add(new IntroCall(getSerializer(directoryProvider, serializers), implMethod, workerName));
            }
        }
        synchronized (services) {
            services.put(serviceClass.getName(), key, localCall);
        }

        // publish the service at the end.
        sharedDataService.write(new RpcSharedData<>(key, serviceClass));
    }

    private Class<?> getParentClass(Class<?> aClass) {
        Class<?>[] declaredClasses = aClass.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            if (!declaredClass.isInterface() && !declaredClass.equals(Object.class)) {
                return declaredClass;
            }
        }
        return null;
    }

    public <T> void addSerializer(Serializer<T> serializer) {
        Serializer put = serializers.put(serializer.getClassType(), serializer);
        if (put != null) {
            throw new RuntimeException("Serializer already registered");
        }
    }

    public void addSerializerAccessor(SerializerAccessor serializerAccessor) {
        serializerAccessors.add(serializerAccessor);
    }

    public <T> List<String> listService(final Class<T> serviceClass) {
        return sharedDataService.read(new SharedDataService.SharedData() {
            List<String> keys;

            public void data(GlobRepository globRepository) {
                GlobList all = globRepository.getAll(ServiceType.TYPE, GlobMatchers.fieldEquals(ServiceType.CLASS_NAME, serviceClass.getName()));
                keys = new ArrayList<>(all.size());
                for (Glob glob : all) {
                    keys.add(glob.get(ServiceType.KEY));
                }
            }
        }).keys;
    }

    public <Service> void unregister(final Class<Service> serviceClass, final String name) {
        sharedDataService.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                Key key = KeyBuilder.create(ServiceType.TYPE)
                        .set(ServiceType.KEY, name)
                        .set(ServiceType.SHARED_ID, sharedDataService.getId())
                        .set(ServiceType.CLASS_NAME, serviceClass.getName()).get();
                if (globRepository.find(key) != null) {
                    globRepository.delete(key);
                }
            }
        });
        synchronized (services) {
            services.remove(serviceClass.getName(), name);
        }
        synchronized (proxy) {
            proxy.remove(serviceClass, name);
        }
    }

    public void clean(Directory directory) {
        logger.info("Stop rpc service on " + serverListener.getUrl());
        try {
            sharedDataService.write(new SharedDataService.SharedData() {
                public void data(GlobRepository globRepository) {
                    globRepository.delete(globRepository.getAll(ServiceType.TYPE,
                            GlobMatchers.fieldEquals(ServiceType.SHARED_ID, sharedDataService.getId())));
                }
            });
        } catch (Exception e) {
            logger.warn("Error while sending delete", e);
        }
        try {
            serverListener.stop();
        } catch (Exception e) {
            logger.warn("Error while stopping listener", e);
        }
        try {
            peerToPeer.destroy();
        } catch (Exception e) {
            logger.warn("Error while destroy peer to peer", e);
        }
        try {
            sharedDataService.stop();
        } catch (Exception e) {
            logger.warn("Error while stopping shared data service", e);
        }
    }

    public void reset() {
        sharedDataService.write(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                globRepository.delete(globRepository.getAll(ServiceType.TYPE,
                        GlobMatchers.fieldEquals(ServiceType.SHARED_ID, sharedDataService.getId())));
            }
        });
    }

    public void dumpRegistered(final Writer writer) {
        sharedDataService.waitForInitialization(1000);
        sharedDataService.read(new SharedDataService.SharedData() {
            public void data(GlobRepository globRepository) {
                GlobPrinter.init(globRepository).run(writer);
            }
        });
    }

    SerializerProvider getSerializer(DirectoryProvider directoryProvider, Serializer[] serializers) {
        if (serializers.length == 0) {
            return new WithLocalDirectorySerializerProvider(directoryProvider, defaultSerializerProvider);
        } else {
            return new DefaultWithAdditionalSerializerProvider(serializers, defaultSerializerProvider, directoryProvider);
        }
    }

    interface SerializerProvider {
        Directory getDirectory();

        Serializer get(Class parameterType);
    }

    interface ArgRW {

        void createName(StringBuilder builder);

        Object read(SerializedInput serializedInput);

        void write(Object object, SerializedOutput serializedOutput);

        ArgRWInOut inOut();
    }

    interface ArgRWInOut {
        void read(Object object, SerializedInput serializedInput);

        void write(Object object, SerializedOutput serializedOutput);
    }

    static class LocalCall {
        Map<String, IntroCall> introCalls = new HashMap<>();
        Object service;

        LocalCall(Object service) {
            this.service = service;
        }

        void call(SerializedInput serializedInput, SerializedOutput serializedOutput) {
            String methodName = serializedInput.readUtf8String();
            IntroCall introCall = introCalls.get(methodName);
            if (introCall == null) {
                throw new RuntimeException("Can not find " + methodName + " on " + service + " got " + introCalls.keySet());
            }
            introCall.call(service, serializedInput, serializedOutput);
        }

        public void add(IntroCall introCall) {
            introCalls.put(introCall.getName(), introCall);

        }
    }

    static class RemoteCall {
        final IntroCall introCall;
        private final String className;

        RemoteCall(String className, IntroCall introCall) {
            this.className = className;
            this.introCall = introCall;
        }

        Object call(PeerToPeer.ClientRequest clientRequest, String key, Object[] values) {
            return introCall.call(className, key, clientRequest, values);
        }
    }

    static class IntroCall {
        private final ArgRW[] args;
        private final ArgRWInOut[] inOut;
        private final ArgRW returnArg;
        private final String name;
        private final Method method;
        private String workerName;

        IntroCall(SerializerProvider serializerProvider, Method method, String workerName) {
            this.method = method;
            this.workerName = workerName;
            boolean hasInOut = false;
            Type[] parameterTypes = method.getGenericParameterTypes();
            args = new ArgRW[parameterTypes.length];
            ArgRWInOut[] inOut = new ArgRWInOut[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = createArgRW(serializerProvider, parameterTypes[i]);
                inOut[i] = args[i].inOut();
                if (inOut[i] != NullArgRW.NULL) {
                    hasInOut = true;
                }
            }
            if (hasInOut) {
                this.inOut = inOut;
            } else {
                this.inOut = new ArgRWInOut[0];
            }
            Type returnType = method.getGenericReturnType();
            returnArg = createArgRW(serializerProvider, returnType);
            StringBuilder nameBuilder = new StringBuilder();
            returnArg.createName(nameBuilder);
            nameBuilder.append(" ").append(method.getName())
                    .append("(");
            for (ArgRW arg : args) {
                arg.createName(nameBuilder);
            }
            nameBuilder.append(")");
            name = nameBuilder.toString();
        }

        public String getName() {
            return name;
        }

        public void call(Object service, SerializedInput serializedInput, SerializedOutput serializedOutput) {
            Object[] args = new Object[this.args.length];
            for (int i = 0; i < this.args.length; i++) {
                ArgRW arg = this.args[i];
                args[i] = arg.read(serializedInput);
            }
            try {
                Object result = method.invoke(service, args);
                serializedOutput.write(Boolean.TRUE);
                for (int i = 0; i < inOut.length; i++) {
                    ArgRWInOut argRW = inOut[i];
                    argRW.write(args[i], serializedOutput);
                }
                returnArg.write(result, serializedOutput);
            } catch (Throwable e) {
                logger.error("exception thrown ", e);
                serializedOutput.write(Boolean.FALSE);
                serializedOutput.writeUtf8String(workerName);
                writeThrowable(e, serializedOutput);
            }
        }

        public Object call(String className, String key, PeerToPeer.ClientRequest clientRequest, Object[] values) {
            try {
                SerializedOutput serializedOutput = SerializedInputOutputFactory.init(clientRequest.getRequestStream());
                serializedOutput.writeUtf8String(className);
                serializedOutput.writeUtf8String(key);
                serializedOutput.writeUtf8String(name);
                for (int i = 0; i < this.args.length; i++) {
                    ArgRW arg = this.args[i];
                    arg.write(values[i], serializedOutput);
                }
                clientRequest.requestComplete();
                SerializedInput serializedInput = SerializedInputOutputFactory.init(clientRequest.getResponseInputStream());
                Boolean status = serializedInput.readBoolean();
                if (status) {
                    for (int i = 0; i < inOut.length; i++) {
                        inOut[i].read(values[i], serializedInput);
                    }
                    return returnArg.read(serializedInput);
                } else {
                    String fromWorker = serializedInput.readUtf8String();
                    Throwable res = readThrowable(serializedInput);
                    throw new RemoteExecutionException("Exception on " + workerName + " due to exception on " + fromWorker, res);
                }
            } catch (RemoteExecutionException e) {
                logger.error("Error", e);
                tryEndRequestAndThrow(clientRequest, e);
                return null;
            } catch (RuntimeException e) {
                logger.error("Error while communicating with remote service: " + e.getMessage(), e);
                tryEndRequestAndThrow(clientRequest, e);
                return null;
            } catch (Throwable e) {
                logger.error("Throwable error while communicating with remote service: " + e.getMessage(), e);
                throw e;
            } finally {
                clientRequest.end();
            }
        }

        private void tryEndRequestAndThrow(PeerToPeer.ClientRequest clientRequest, RuntimeException e) {
            try {
                clientRequest.end();
            } catch (RuntimeException exec) {
                exec.initCause(e);
                throw exec;
            }
            throw e;
        }

    }

    private static class RemoteExecutionException extends RuntimeException {
        private RemoteExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class RemoteThrowable extends Throwable {
        private String toString;

        private RemoteThrowable(String message, String toString) {
            super(message);
            this.toString = toString;
        }

        public String toString() {
            return toString;
        }
    }

    static class NullArgRW implements ArgRWInOut {
        static NullArgRW NULL = new NullArgRW();

        public void read(Object object, SerializedInput serializedInput) {
        }

        public void write(Object object, SerializedOutput serializedOutput) {
        }
    }

    static abstract class AbstractNamedArg implements ArgRW {
        final Class<?> parameterType;

        protected AbstractNamedArg(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        public void createName(StringBuilder builder) {
            builder.append(parameterType.getName());
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    static class IntArgRW extends AbstractNamedArg {

        public IntArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readInteger();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeInteger((Integer) object);
        }
    }

    static class LongArgRW extends AbstractNamedArg {

        public LongArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readLong();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeLong((Long) object);
        }
    }

    static class BooleanArgRW extends AbstractNamedArg {

        protected BooleanArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readBoolean();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeBoolean((Boolean) object);
        }
    }

    static class CharacterArgRW extends AbstractNamedArg {

        protected CharacterArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            String s = serializedInput.readUtf8String();
            return s == null ? null : s.charAt(0);
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeUtf8String(Character.toString((Character) object));
        }
    }

    static class ByteArgRW extends AbstractNamedArg {

        protected ByteArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readByte();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeByte((Byte) object);
        }
    }

    static class StringArgRW extends AbstractNamedArg {

        protected StringArgRW(Class<?> parameterType) {
            super(parameterType);
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readUtf8String();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeUtf8String((String) object);
        }
    }

    private static class PreloadedInvocationHandler<Service> implements InvocationHandler, SharedDataService.SharedDataEventListener {
        private final Class<Service> serviceClass;
        private final String key;
        private final SharedDataService sharedDataService;
        private final PeerToPeer peerToPeer;
        private Map<Method, RemoteCall> methodCallMap = new HashMap<>();
        private volatile Pair<String, String> realKeyAndUrl;

        public PreloadedInvocationHandler(String workerName, Class<Service> serviceClass, String key, boolean listen, SharedDataService sharedDataService,
                                          PeerToPeer peerToPeer, SerializerProvider serializerProvider) {
            this.serviceClass = serviceClass;
            this.key = key;
            this.sharedDataService = sharedDataService;
            this.peerToPeer = peerToPeer;
            Method[] methods = serviceClass.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(ExportMethod.class)) {
                    methodCallMap.put(method, new RemoteCall(serviceClass.getName(), new IntroCall(serializerProvider, method, workerName)));
                }
            }
            if (listen) {
                sharedDataService.listen(new SoftSharedDataEventListener(sharedDataService, this));
            }
        }

        // multiple thread can call this.
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (logger.isDebugEnabled()) {
                logger.debug("Rpc call to '" + method.getName() + "'");
            }
            final RemoteCall remoteCall = methodCallMap.get(method);
            if (remoteCall == null) {
                throw new RuntimeException(method.getName() + " not found on " + serviceClass.getName() + " got " + methodCallMap.keySet());
            }
            final Pair<String, String> url = getServiceUrl();
            final PeerToPeer.ClientRequestFactory clientRequestFactory = peerToPeer.getClientRequestFactory(url.getSecond());
            final PeerToPeer.ClientRequest clientRequest = clientRequestFactory.create();
            try {
                return remoteCall.call(clientRequest, url.getFirst(), args);
            } finally {
                clientRequestFactory.release();
                if (logger.isDebugEnabled()) {
                    logger.debug("Rpc call complete");
                }
            }
        }

        private Pair<String, String> getServiceUrl() {
            Pair<String, String> url = this.realKeyAndUrl;
            if (url != null) {
                return url;
            }
            long now = System.currentTimeMillis();
            long end = now + MAX_WAIT_RPC_PROXY;
            do {
                url = this.realKeyAndUrl;
                if (url == null) {
                    url = getKeyAndUrl();
                    this.realKeyAndUrl = url;
                }
                if (url == null) {
                    try {
                        Thread.sleep(100);
                        now = System.currentTimeMillis();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("in getUrl", e);
                    }
                }
            } while (url == null && now < end);
            if (url == null) {
                String message = "Can not find " + serviceClass.getName() + " named '" + key + "'";
                String msg = sharedDataService.read(new SharedDataService.SharedData() {
                    private String msg;

                    public void data(GlobRepository globRepository) {
                        msg = GlobPrinter.init(globRepository).toString();
                    }
                }).msg;
                logger.error(message + " got " + msg);
                throw new RuntimeException(message);
            }
            return url;
        }

        public void event(ChangeSet changeSet) {
            if (changeSet.containsChanges(ServiceType.TYPE)) {
                reset();
            }
        }

        public void reset() {
            realKeyAndUrl = getKeyAndUrl();
        }

        private Pair<String, String> getKeyAndUrl() {
            return sharedDataService.read(new SharedDataService.SharedData() {
                Pair<String, String> url;

                public void data(GlobRepository globRepository) {
                    url = getKeyAndUrl(globRepository);
                }


            }).url;
        }

        private Pair<String, String> getKeyAndUrl(GlobRepository globRepository) {
            ReadOnlyGlobRepository.MultiFieldIndexed byIndex = globRepository.findByIndex(ServiceType.SERVICE_INDEX, ServiceType.CLASS_NAME, serviceClass.getName());
            if (key != null) {
                byIndex = byIndex.findByIndex(ServiceType.KEY, key);
            }
            GlobList services1 = byIndex.getGlobs();
            if (services1.isEmpty()) {
                return null;
            } else {
                Glob glob = services1.get(0);
                return Pair.makePair(glob.get(ServiceType.KEY), glob.get(ServiceType.URL));
            }
        }

        private static class SoftSharedDataEventListener implements SharedDataService.SharedDataEventListener {
            SoftReference<SharedDataService.SharedDataEventListener> listenerWeakReference;
            private SharedDataService sharedDataService;

            public SoftSharedDataEventListener(SharedDataService sharedDataService, SharedDataService.SharedDataEventListener sharedDataEventListener) {
                this.sharedDataService = sharedDataService;
                listenerWeakReference = new SoftReference<>(sharedDataEventListener);
            }

            public void event(ChangeSet changeSet) {
                SharedDataService.SharedDataEventListener sharedDataEventListener = listenerWeakReference.get();
                if (sharedDataEventListener == null) {
                    sharedDataService.remove(this);
                } else {
                    sharedDataEventListener.event(changeSet);
                }
            }

            public void reset() {
                SharedDataService.SharedDataEventListener sharedDataEventListener = listenerWeakReference.get();
                if (sharedDataEventListener == null) {
                    sharedDataService.remove(this);
                } else {
                    sharedDataEventListener.reset();
                }

            }
        }
    }

    private static class RefArgRW implements ArgRW, ArgRWInOut {
        private final ArgRW argRW1;
        private final Type type;

        public RefArgRW(Type type, ArgRW argRW1) {
            this.type = type;
            this.argRW1 = argRW1;
        }

        public void createName(StringBuilder builder) {
            builder.append(type.toString());
        }

        public Object read(SerializedInput serializedInput) {
            if (serializedInput.readBoolean()) {
                return new Ref(argRW1.read(serializedInput));
            } else {
                return new Ref();
            }
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            Object value = ((Ref) object).get();
            if (value == null) {
                serializedOutput.write(false);
            } else {
                serializedOutput.write(true);
                argRW1.write(value, serializedOutput);
            }
        }

        public ArgRWInOut inOut() {
            return this;
        }

        public void read(Object object, SerializedInput serializedInput) {
            if (serializedInput.readBoolean()) {
                ((Ref) object).set(argRW1.read(serializedInput));
            }
        }
    }

    private static class ListArgRW implements ArgRW {
        private final Type type;
        private final ArgRW argRW1;

        public ListArgRW(Type type, ArgRW argRW1) {
            this.type = type;
            this.argRW1 = argRW1;
        }

        public void createName(StringBuilder builder) {
            builder.append("List<").append(type.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            int len = serializedInput.readNotNullInt();
            if (len < 0) {
                return null;
            }
            ArrayList list = new ArrayList(len);
            while (len > 0) {
                list.add(argRW1.read(serializedInput));
                len--;
            }
            return list;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            List list = (List) object;
            serializedOutput.write(list == null ? -1 : list.size());
            if (list != null) {
                for (Object o : list) {
                    argRW1.write(o, serializedOutput);
                }
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class SetArgRW implements ArgRW {
        private final Type type;
        private final ArgRW argRW1;

        public SetArgRW(Type type, ArgRW argRW1) {
            this.type = type;
            this.argRW1 = argRW1;
        }

        public void createName(StringBuilder builder) {
            builder.append("Set<").append(type.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            int len = serializedInput.readNotNullInt();
            if (len < 0) {
                return null;
            }
            HashSet set = new HashSet(len);
            while (len > 0) {
                set.add(argRW1.read(serializedInput));
                len--;
            }
            return set;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            Set set = (Set) object;
            serializedOutput.write(set == null ? -1 : set.size());
            if (set != null) {
                for (Object o : set) {
                    argRW1.write(o, serializedOutput);
                }
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class MapArgRW implements ArgRW {
        private final Type type1;
        private final Type type2;
        private final ArgRW argRW1;
        private final ArgRW argRW2;

        public MapArgRW(Type Type1, ArgRW argRW1, Type Type2, ArgRW argRW2) {
            this.type1 = Type1;
            this.argRW1 = argRW1;
            this.type2 = Type2;
            this.argRW2 = argRW2;
        }

        public void createName(StringBuilder builder) {
            builder.append("Map<").append(type1.toString()).append(", ").append(type2.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            int len = serializedInput.readNotNullInt();
            if (len == -1) {
                return null;
            }
            HashMap map = new HashMap(Utils.hashMapOptimalCapacity(len));
            while (len > 0) {
                map.put(argRW1.read(serializedInput), argRW2.read(serializedInput));
                len--;
            }
            return map;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            Map map = (Map) object;
            serializedOutput.write(map == null ? -1 : map.size());
            if (map != null) {
                Set<Map.Entry> set = map.entrySet();
                for (Map.Entry o : set) {
                    argRW1.write(o.getKey(), serializedOutput);
                    argRW2.write(o.getValue(), serializedOutput);
                }
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class PairArgRW implements ArgRW {
        private final Type type1;
        private final Type type2;
        private final ArgRW argRW1;
        private final ArgRW argRW2;

        public PairArgRW(Type Type1, ArgRW argRW1, Type Type2, ArgRW argRW2) {
            this.type1 = Type1;
            this.argRW1 = argRW1;
            this.type2 = Type2;
            this.argRW2 = argRW2;
        }

        public void createName(StringBuilder builder) {
            builder.append("Pair<").append(type1.toString()).append(", ").append(type2.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            Object o1 = argRW1.read(serializedInput);
            Object o2 = argRW2.read(serializedInput);
            return new Pair(o1, o2);
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            Pair p = (Pair) object;
            argRW1.write(p.getFirst(), serializedOutput);
            argRW2.write(p.getSecond(), serializedOutput);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class MultiMapArgRW implements ArgRW {
        private final Type type1;
        private final Type type2;
        private final ArgRW argRW1;
        private final ArgRW argRW2;

        public MultiMapArgRW(Type Type1, ArgRW argRW1, Type Type2, ArgRW argRW2) {
            this.type1 = Type1;
            this.argRW1 = argRW1;
            this.type2 = Type2;
            this.argRW2 = argRW2;
        }

        public void createName(StringBuilder builder) {
            builder.append("MultiMap<").append(type1.toString()).append(", ").append(type2.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            int len = serializedInput.readNotNullInt();
            MultiMap map = new MultiMap(len);
            while (len > 0) {
                Object k = argRW1.read(serializedInput);
                int lenList = serializedInput.readNotNullInt();
                List v = new ArrayList(lenList);
                while (lenList > 0) {
                    v.add(argRW2.read(serializedInput));
                    lenList--;
                }
                map.replaceAll(k, v);
                len--;
            }
            return map;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            MultiMap map = (MultiMap) object;
            serializedOutput.write(map == null ? 0 : map.keySize());
            if (map != null) {
                Set<Map.Entry> set = map.entries();
                for (Map.Entry o : set) {
                    argRW1.write(o.getKey(), serializedOutput);
                    List value = (List) o.getValue();
                    serializedOutput.write(value.size());
                    for (Object element : value) {
                        argRW2.write(element, serializedOutput);
                    }
                }
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class GloballySharedArgRW implements ArgRW {
        private final ParameterizedType parameterizedType;
        private final ArgRW argRW1;
        private SerializerProvider serializerProvider;

        public GloballySharedArgRW(SerializerProvider serializerProvider, ParameterizedType parameterizedType, ArgRW argRW1) {
            this.serializerProvider = serializerProvider;
            this.parameterizedType = parameterizedType;
            this.argRW1 = argRW1;
        }

        public void createName(StringBuilder builder) {
            builder.append("GloballyShared<").append(parameterizedType.toString()).append(">");
        }

        public Object read(SerializedInput serializedInput) {
            Object read = argRW1.read(serializedInput);
            SharedService sharedService = serializerProvider.getDirectory().get(SharedService.class);
            return sharedService.restoreShared(serializedInput, read);
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            Shared ref = (Shared) object;
            argRW1.write(ref.get(), serializedOutput);
            ref.postSave(serializedOutput);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class FromSerializerNamedArg extends AbstractNamedArg {
        private final Serializer serializer;
        private SerializerProvider serializerProvider;

        public FromSerializerNamedArg(Type parameterType, Serializer serializer, SerializerProvider serializerProvider) {
            super((Class<?>) parameterType);
            this.serializer = serializer;
            this.serializerProvider = serializerProvider;
        }

        public Object read(SerializedInput serializedInput) {
            return serializer.read(serializedInput, serializerProvider.getDirectory());
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializer.write(object, serializedOutput);
        }
    }

    private static class VoidArgRW implements ArgRW {
        public void createName(StringBuilder builder) {
        }

        public Object read(SerializedInput serializedInput) {
            return null;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private static class DefaultWithAdditionalSerializerProvider implements SerializerProvider {
        private final SerializerProvider serializerProvider;
        private DirectoryProvider directoryProvider;
        private Map<Class, Serializer> serializerMap = new HashMap<>();

        public DefaultWithAdditionalSerializerProvider(Serializer[] serializers, SerializerProvider serializerProvider, DirectoryProvider directoryProvider) {
            this.serializerProvider = serializerProvider;
            this.directoryProvider = directoryProvider;
            for (Serializer serializer : serializers) {
                serializerMap.put(serializer.getClassType(), serializer);
            }
        }

        public Directory getDirectory() {
            return directoryProvider.getDirectory();
        }

        public Serializer get(Class parameterType) {
            Serializer serializer = serializerMap.get(parameterType);
            if (serializer != null) {
                return serializer;
            }
            return serializerProvider.get(parameterType);
        }
    }

    private static class DefaultSerializerProvider implements SerializerProvider {
        final DefaultRpcService defaultRpcService;
        private DirectoryProvider directoryProvider;

        private DefaultSerializerProvider(DefaultRpcService defaultRpcService, DirectoryProvider directoryProvider) {
            this.defaultRpcService = defaultRpcService;
            this.directoryProvider = directoryProvider;
        }

        public Directory getDirectory() {
            return directoryProvider.getDirectory();
        }

        public Serializer get(Class parameterType) {
            Serializer serializer = defaultRpcService.serializers.get(parameterType);
            if (serializer != null) {
                return serializer;
            }
            for (SerializerAccessor serializerAccessor : defaultRpcService.serializerAccessors) {
                serializer = serializerAccessor.getSerializer(parameterType);
                if (serializer != null) {
                    break;
                }
            }
            return serializer;
        }
    }

    private static class WithLocalDirectorySerializerProvider implements SerializerProvider {
        private final DirectoryProvider directoryProvider;
        private final SerializerProvider defaultSerializerProvider;

        public WithLocalDirectorySerializerProvider(DirectoryProvider directoryProvider, SerializerProvider defaultSerializerProvider) {
            this.directoryProvider = directoryProvider;
            this.defaultSerializerProvider = defaultSerializerProvider;
        }

        public Directory getDirectory() {
            return directoryProvider.getDirectory();
        }

        public Serializer get(Class parameterType) {
            return defaultSerializerProvider.get(parameterType);
        }
    }

    public static class ByteArrayArgRW implements ArgRW {

        public void createName(StringBuilder builder) {
            builder.append("byte[]");
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readBytes();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.writeBytes((byte[]) object);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    public static class DoubleArrayArgRW implements ArgRW {

        public void createName(StringBuilder builder) {
            builder.append("double[]");
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readDoubleArray();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.write((double[]) object);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    public static class LongArrayArgRW implements ArgRW {

        public void createName(StringBuilder builder) {
            builder.append("long[]");
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readLongArray();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.write((long[]) object);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    public static class IntArrayArgRW implements ArgRW {

        public void createName(StringBuilder builder) {
            builder.append("int[]");
        }

        public Object read(SerializedInput serializedInput) {
            return serializedInput.readIntArray();
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            serializedOutput.write((int[]) object);
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    public static class ArrayArgRW implements ArgRW {
        private final Class<?> componentType;
        private final ArgRW argRW;

        public ArrayArgRW(Class<?> componentType, ArgRW argRW) {
            this.componentType = componentType;
            this.argRW = argRW;
        }

        public void createName(StringBuilder builder) {
            argRW.createName(builder);
            builder.append("[]");
        }

        public Object read(SerializedInput serializedInput) {
            int len = serializedInput.readNotNullInt();
            if (len == -1) {
                return null;
            }
            Object o = Array.newInstance(componentType, len);
            for (int i = 0; i < len; i++) {
                Array.set(o, i, argRW.read(serializedInput));
            }
            return o;
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            if (object == null) {
                serializedOutput.write(-1);
            }
            int length = Array.getLength(object);
            serializedOutput.write(length);
            for (int i = 0; i < length; i++) {
                argRW.write(Array.get(object, i), serializedOutput);
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    public static class SerializableArg implements ArgRW {

        private final Class parameterType;

        public SerializableArg(Class parameterType) {
            this.parameterType = parameterType;
        }

        public void createName(StringBuilder builder) {
            builder.append(parameterType.getName());
        }

        public Object read(SerializedInput serializedInput) {
            try {
                byte[] bytes = serializedInput.readBytes();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                return objectInputStream.readObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void write(Object object, SerializedOutput serializedOutput) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
                objectOutputStream.writeObject(object);
                serializedOutput.writeBytes(out.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public ArgRWInOut inOut() {
            return NullArgRW.NULL;
        }
    }

    private class RpcSharedData<Service> implements SharedDataService.SharedData {
        private final String key;
        private final Class<Service> serviceClass;

        public RpcSharedData(String key, Class<Service> serviceClass) {
            this.key = key;
            this.serviceClass = serviceClass;
        }

        public void data(GlobRepository globRepository) {
            globRepository.create(ServiceType.TYPE,
                    FieldValue.value(ServiceType.SHARED_ID, sharedDataService.getId()),
                    FieldValue.value(ServiceType.KEY, key),
                    FieldValue.value(ServiceType.URL, serverListener.getUrl()),
                    FieldValue.value(ServiceType.CLASS_NAME, serviceClass.getName()));
        }
    }

//    private static class PropertyContextArg implements ArgRW {
//        private SerializerProvider serializerProvider;
//
//        public PropertyContextArg(SerializerProvider serializerProvider) {
//            this.serializerProvider = serializerProvider;
//        }
//
//        public void createName(StringBuilder builder) {
//            builder.append("PropertyContext");
//        }
//
//        public Object read(SerializedInput serializedInput) {
//            return PropertyContext.Restore.restore(serializerProvider.getDirectory(), serializedInput);
//        }
//
//        public void write(Object object, SerializedOutput serializedOutput) {
//            PropertyContext.Save.save((PropertyContext) object, serializedOutput);
//        }
//
//        public ArgRWInOut inOut() {
//            return NullArgRW.NULL;
//        }
//    }
}
