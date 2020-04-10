package org.globsframework.remote.peer.direct;

import org.globsframework.remote.peer.PeerToPeer;
import org.globsframework.remote.peer.PeerToPeerFactory;

public class DirectPeerToPeerFactory implements PeerToPeerFactory {
    public PeerToPeer createPeerToPeer() {
        return new DirectPeerToPeer();
    }

    public PeerToPeer createPeerToPeer(String host) {
        return new DirectPeerToPeer(host);
    }
}
