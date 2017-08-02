package io.tourniquet.proxy;

import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 * Dispatcher that directs processing data to a configurable data handlers. All data handlers can be assigned to either
 * outgoing requests or incoming responses. Data Handlers may register themselves at the data dispatcher after startup.
 * And may be used afterwards. During startup ensure that the data handlers are only deployed after the data dispatcher
 * is deployed to ensure they register correctly. The configuration of the dispatcher can be read through the
 * 'dispatcher/config' address. Data can be processed and dispatched through the 'incoming' and 'outgoing' address (see
 * link {@link TransmissionDirection})
 */
public class DataDispatcherVerticle extends AbstractVerticle {

   private static final Logger LOG = getLogger(DataDispatcherVerticle.class);

   /**
    * Registry of all available handlers. Each handler can be used for incoming or outgoing traffic
    */
   private Map<String, Handler<Message<Buffer>>> handlerRegistry = new HashMap<>();
   /**
    * Mapping of transmission directions to a current handler address.
    */
   private Map<TransmissionDirection, String> currentHandler = new HashMap<>();

   private static final String NOOP_ADDR = "";

   @Override
   public void start() throws Exception {

      this.handlerRegistry.put(NOOP_ADDR, msg -> msg.reply(msg.body()));

      final EventBus eb = vertx.eventBus();
      for (TransmissionDirection dir : TransmissionDirection.values()) {
         this.currentHandler.put(dir, NOOP_ADDR);
         eb.consumer(dir.getAddress(), dispatch(dir));
         eb.consumer("dispatcher/" + dir.getAddress() + "/set", setHandler(dir));
      }

      eb.consumer("dispatcher/register", this::registerHandler);
      eb.consumer("dispatcher/config",
                  msg -> msg.reply(new JsonObject().put("incoming", this.currentHandler.get(TransmissionDirection.RCV))
                                                   .put("outgoing", this.currentHandler.get(TransmissionDirection.SND))
                                                   .put("handlers",
                                                        new JsonArray(handlerRegistry.keySet()
                                                                                     .stream()
                                                                                     .filter(h -> !h.isEmpty())
                                                                                     .collect(toList())))));

   }

   private Handler<Message<Buffer>> dispatch(TransmissionDirection dir) {

      return msg -> handlerRegistry.get(currentHandler.get(dir)).handle(msg);
   }

   private Handler<Message<String>> setHandler(TransmissionDirection dir) {

      return msg -> {
         final String handlerAddress = msg.body();
         if (handlerRegistry.containsKey(handlerAddress)) {
            this.currentHandler.put(dir, handlerAddress);
            msg.reply("ok");
         } else {
            msg.fail(400, "Unknown handler " + handlerAddress);
         }
      };
   }

   private void registerHandler(Message<String> msg) {

      final String handlerAddress = msg.body();
      if (handlerAddress.isEmpty()) {
         msg.fail(400, "Handler address must not be empty");
      } else {
         this.handlerRegistry.put(handlerAddress, processData(handlerAddress));
         LOG.info("Registered handler '{}'", handlerAddress);
         msg.reply("ok");
      }
   }

   private Handler<Message<Buffer>> processData(String addr) {

      return msg -> vertx.eventBus().send(addr + "/data", msg.body(), reply -> {
         if (reply.succeeded()) {
            msg.reply(reply.result().body());
         } else {
            msg.fail(500, reply.cause().getMessage());
            LOG.warn("Could not process data", reply.cause());
         }
      });
   }

}
