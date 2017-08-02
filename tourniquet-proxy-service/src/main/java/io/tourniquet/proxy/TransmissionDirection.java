package io.tourniquet.proxy;

enum TransmissionDirection {
    SND("outgoing"),
    RCV("incoming");

    private final String address;

    TransmissionDirection(final String eventbusAddress) {

        this.address = eventbusAddress;
    }

    public String getAddress() {

        return address;
    }
}
