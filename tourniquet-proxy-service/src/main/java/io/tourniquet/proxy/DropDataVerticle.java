package io.tourniquet.proxy;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;

/**
 * Data handler that drops packages by sending an empty buffer
 */
public class DropDataVerticle extends DataHandlerVerticle {

    private static final Logger LOG = getLogger(DropDataVerticle.class);

    private final Buffer EMPTY = Buffer.buffer();

    @Override
    protected String identifier() {
        return "drop";
    }

    @Override
    protected void handleData(final Message<Buffer> msg) {
        if(LOG.isTraceEnabled()){
            LOG.trace("Dropping {} bytes", msg.body().length());
        }

        vertx.eventBus().publish("/monitor", Instant.now() + " Dropping data of length " + msg.body().length());
        msg.reply(EMPTY);
    }



}
