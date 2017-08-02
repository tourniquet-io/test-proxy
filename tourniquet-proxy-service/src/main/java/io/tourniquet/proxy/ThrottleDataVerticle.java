package io.tourniquet.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;

public class ThrottleDataVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer("incoming", this::incomingDataHandler);
        vertx.eventBus().consumer("outgoing", this::outgoingDataHandler);
    }



    private void incomingDataHandler(final Message<Object> msg) {
        //drop the data by returning an empty buffer
        msg.reply(Buffer.buffer());
    }

    private void outgoingDataHandler(final Message<Object> msg) {
        //drop the data by returning an empty buffer
        msg.reply(Buffer.buffer());
    }

}
