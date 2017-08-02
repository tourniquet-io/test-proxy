package io.tourniquet.proxy;

import java.time.Instant;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;

public class DropDataVerticle extends DataHandlerVerticle {

    private final Buffer EMPTY = Buffer.buffer();

    @Override
    protected String identifier() {
        return "drop";
    }

    @Override
    protected void handleIncoming(final Message<Buffer> inMsg) {
        vertx.eventBus().publish("/monitor", Instant.now() + " Dropping incoming data of length " + inMsg.body().length());
        inMsg.reply(EMPTY);
    }

    @Override
    protected void handleOutgoing(final Message<Buffer> outMsg) {
        vertx.eventBus().publish("/monitor", Instant.now() + " Dropping outgoing data of length " + outMsg.body().length());
        outMsg.reply(EMPTY);
    }



}
