package org.globsframework.remote.shared;


import org.globsframework.utils.collections.Pair;

public interface AddressAccessor {
    Pair<String, Integer> getHostAndPort();

    class FixAddressAccessor implements AddressAccessor {
        public final String host;
        public final int port;

        public FixAddressAccessor(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public Pair<String, Integer> getHostAndPort() {
            return new Pair<String, Integer>(host, port);
        }

        static public AddressAccessor create(String host, int port){
            return new FixAddressAccessor(host, port);
        }
    }
}
