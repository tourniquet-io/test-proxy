package io.tourniquet.proxy;

import java.time.Instant;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;

/**
 * Data handler that drops packages by sending an empty buffer
 */
public class DropDataVerticle extends DataHandlerVerticle {

    private final Buffer EMPTY = Buffer.buffer();

    @Override
    protected String identifier() {
        return "drop";
    }

    @Override
    protected void handleData(final Message<Buffer> outMsg) {
        vertx.eventBus().publish("/monitor", Instant.now() + " Dropping data of length " + outMsg.body().length());
        outMsg.reply(EMPTY);
    }



}
