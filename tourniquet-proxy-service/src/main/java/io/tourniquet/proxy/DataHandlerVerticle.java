package io.tourniquet.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Basic Datahandler to extend with functionality. The datahandler listens on the "incoming" and "outgoing" address as well as the "::config" address (prefix by the identifier).
 * <br>
 * Processing of outgoing and incoming data can be activated and deactivated by sending a configuration with the boolean properties "enableIncoming" and "enableOutgoing".
 * Default setting is deactivated, which will passthrough the data. If no such keys are present, the setting remains unchanged.
 */
public abstract class DataHandlerVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {


        if(direction() == Direction.IN || direction() == Direction.BOTH){
            vertx.eventBus().consumer(identifier() + "/incoming", this::handleIncoming);
            vertx.eventBus().send("dispatcher/incoming/register", identifier() + "/incoming");
        }
        if(direction() == Direction.OUT || direction() == Direction.BOTH){
            vertx.eventBus().consumer(identifier() + "/outgoing", this::handleOutgoing);
            vertx.eventBus().send("dispatcher/outgoing/register", identifier() + "/outgoing");
        }
        vertx.eventBus().consumer(identifier() + "/config", this::configHandler);
    }

    /**
     * An opaque string for addressing the concrete handler.
     *
     * @return
     */
    protected abstract String identifier();

    /**
     * The direction of the data handling. Default is BOTH.
     * @return
     */
    protected Direction direction(){
        return Direction.BOTH;
    }

    /**
     * Handles configuration messages. The two default fields - "enableIncoming" and "enableOutgoing" will activate or deactivate the processing of incoming or outgoing data.
     * Default setting is deactivated. If no such keys are present, the setting remains unchanged.
     *
     * @param cfgMsg
     *         the configuration message to process
     */
    private void configHandler(final Message<JsonObject> cfgMsg) {

        JsonObject config = cfgMsg.body();

        configure(config);

    }

    /**
     * Default method to pass through data. The method mirrors the body back to the sender.
     *
     * @param msg
     *         the incoming message
     */
    protected void passthrough(final Message<Buffer> msg) {

        msg.reply(msg.body());
    }

    /**
     * Implement this method to receive and process configuration updates.
     *
     * @param config
     *         the configuration to apply
     */
    protected void configure(final JsonObject config) {

    }

    /**
     * Implement this method to handle incoming data.
     *
     * @param inMsg
     */
    protected void handleIncoming(final Message<Buffer> inMsg) {

        passthrough(inMsg);
    }

    /**
     * Implement this method to handle outgoing data.
     *
     * @param outMsg
     */
    protected void handleOutgoing(final Message<Buffer> outMsg) {

        passthrough(outMsg);
    }

    protected enum Direction{

        /**
         * Only incoming data (from the server to the client)
         */
        IN,
        /**
         * Only outgoing data (from the client to the server)
         */
        OUT,
        /**
         * Both directions (incoming & outgoing)
         */
        BOTH;

    }
}
