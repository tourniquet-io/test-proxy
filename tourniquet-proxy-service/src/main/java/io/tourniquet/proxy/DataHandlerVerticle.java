package io.tourniquet.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Basic Datahandler to extend with functionality. The datahandler listens on the "incoming" and "outgoing" address as
 * well as the "::config" address (prefix by the identifier).
 * <br>
 * Processing of outgoing and incoming data can be activated and deactivated by sending a configuration with the boolean
 * properties "enableIncoming" and "enableOutgoing". Default setting is deactivated, which will passthrough the data.
 * If no such keys are present, the setting remains unchanged.
 */
public abstract class DataHandlerVerticle extends AbstractVerticle {

   @Override
   public void start() throws Exception {

      vertx.eventBus().send("dispatcher/register", identifier());
      vertx.eventBus().consumer(identifier() + "/data", this::handleData);
      vertx.eventBus().consumer(identifier() + "/config", this::configHandler);
   }

   /**
    * An opaque string for addressing the concrete handler.
    *
    * @return
    */
   protected abstract String identifier();

   /**
    * Handles configuration messages. The two default fields - "enableIncoming" and "enableOutgoing" will activate or
    * deactivate the processing of incoming or outgoing data.
    * Default setting is deactivated. If no such keys are present, the setting remains unchanged.
    *
    * @param cfgMsg
    *         the configuration message to process
    */
   private void configHandler(final Message<JsonObject> cfgMsg) {

      configure(cfgMsg.body());
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
    * Default method to pass through data. The method mirrors the body back to the sender.
    *
    * @param msg
    *         the incoming message
    */
   protected void passthrough(final Message<Buffer> msg) {

      msg.reply(msg.body());
   }

   /**
    * Implement this method to handle outgoing data.
    *
    * @param outMsg
    */
   protected void handleData(final Message<Buffer> outMsg) {

      passthrough(outMsg);
   }

}
