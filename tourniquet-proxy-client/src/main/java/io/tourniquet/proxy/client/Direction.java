package io.tourniquet.proxy.client;

public enum Direction {

    /**
     * Requests sent from a client application over the proxy to the target server
     */
    OUTGOING,
    /**
     * Responses returned from the target server to the client
     */
    INCOMING,
    ;

    @Override
    public String toString() {

        return super.toString().toLowerCase();
    }
}
