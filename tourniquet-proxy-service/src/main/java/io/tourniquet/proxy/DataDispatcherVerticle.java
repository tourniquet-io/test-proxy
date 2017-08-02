package io.tourniquet.proxy;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

public class DataDispatcherVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(DataDispatcherVerticle.class);

    private Set<String> incomingHandlers = new HashSet<>();
    private Set<String> outgoingHandlers = new HashSet<>();

    private String incoming = "";
    private String outgoing = "";

    @Override
    public void start() throws Exception {

        vertx.eventBus().consumer("incoming", dispatch(() -> incoming));
        vertx.eventBus().consumer("dispatcher/incoming/set", this::setIncoming);
        vertx.eventBus().consumer("dispatcher/incoming/register", this::registerIncoming);

        vertx.eventBus().consumer("outgoing", dispatch(() -> outgoing));
        vertx.eventBus().consumer("dispatcher/outgoing/set", this::setOutgoing);
        vertx.eventBus().consumer("dispatcher/outgoing/register", this::registerOutgoing);

        vertx.eventBus()
             .consumer("dispatcher/config",
                       msg -> msg.reply(new JsonObject().put("incoming", incoming)
                                                        .put("outgoing", outgoing)
                                                        .put("incomingHandlers", new JsonArray(incomingHandlers.stream().collect(Collectors.toList())))
                                                        .put("outgoingHandlers", new JsonArray(outgoingHandlers.stream().collect(Collectors.toList())))));

    }

    private Handler<Message<Buffer>> dispatch(final Supplier<String> addr) {

        return msg -> dispatch(msg, addr.get());

    }

    private void dispatch(final Message<Buffer> msg, final String addr) {

        if (!addr.isEmpty()) {
            vertx.eventBus().send(addr, msg.body(), reply -> {
                if (reply.succeeded()) {
                    msg.reply(reply.result().body());
                } else {
                    msg.fail(500, reply.cause().getMessage());
                    LOG.warn("Could not process data", reply.cause());
                }
            });
        } else {
            msg.reply(msg.body());
        }
    }

    private void setIncoming(final Message<String> msg) {

        String addr = msg.body();
        if (this.incomingHandlers.contains(addr) || addr.isEmpty()) {
            this.incoming = addr;
            msg.reply("ok");
        } else {
            msg.fail(400, "Unknown handler " + addr);
        }
    }

    private void setOutgoing(final Message<String> msg) {

        String addr = msg.body();
        if (this.outgoingHandlers.contains(addr) || addr.isEmpty()) {
            this.outgoing = addr;
            msg.reply("ok");
        } else {
            msg.fail(400, "Unknown handler " + addr);
        }
    }

    private void registerIncoming(final Message<String> msg) {

        final String handler = msg.body();
        this.incomingHandlers.add(handler);
        LOG.info("Registered handler '{}'", handler);
        msg.reply("ok");
    }

    private void registerOutgoing(final Message<String> msg) {

        final String handler = msg.body();
        this.outgoingHandlers.add(handler);
        LOG.info("Registered handler '{}'", handler);
        msg.reply("ok");

    }

}
