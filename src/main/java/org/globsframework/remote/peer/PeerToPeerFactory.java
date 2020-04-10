package org.globsframework.remote.peer;

public interface PeerToPeerFactory {

    PeerToPeer createPeerToPeer();

    PeerToPeer createPeerToPeer(String host);
}
