package io.tourniquet.proxy;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Duration;
import java.time.Instant;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;

/**
 * Configuration handler for the proxy. The following endpoints can be used to configure the proxy:
 * <p>
 * <ul> <li><code>/config</code> <ul> <li>GET - retrieves the current handler dispatcher configuration</li>
 * <li>PUT - enables a specifc data handler. Must be a valid handler from the list (see GET), use empty string for
 * disable proxy for that direction<pre>
 *                  { "incoming" : "handlerName"}
 *              </pre></li>
 * </ul> </li> <li><code>/config/ttr</code> <ul> <li>POST - sets a timer for resetting the proxy (disabling handler for
 * direction)
 * <pre>{
 *                  "dir" : "incoming" or "outgoing"
 *                  "ttr" : "1...14400" minutes until reset
 *              }</pre></li>
 * </ul>
 *
 * </li> </ul>
 */
public class ConfigVerticle extends AbstractVerticle {

   private static final Logger LOG = getLogger(ConfigVerticle.class);

   @Override
   public void start(final Future<Void> startFuture) throws Exception {

      final JsonObject config = config();
      final int configPort = config.getInteger("configPort", 7099);
      final HttpServerOptions opts = new HttpServerOptions().setPort(configPort);

      final Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create());

      router.put("/config").handler(this::putConfig);
      router.post("/config/ttr").handler(this::postTTR);
      router.get("/config").handler(ctx -> getDispatcherConfig(cfg -> respondConfig(ctx, cfg)));
      router.route("/eb/*")
            .handler(SockJSHandler.create(vertx)
                                  .bridge(new BridgeOptions().addOutboundPermitted(new PermittedOptions())));
      router.route().handler(StaticHandler.create());

      vertx.createHttpServer(opts).requestHandler(router::accept).listen(complete -> {
         LOG.info("Configuration Handler startup complete, config listening on {}", configPort);
         startFuture.complete();
      });
   }

   private void postTTR(final RoutingContext ctx) {

      final JsonObject config = ctx.getBodyAsJson();
      final String dir = config.getString("dir");
      final Duration ttr = Duration.ofMinutes(Long.parseLong(config.getString("ttr")));

      LOG.info("Setting Reset timer to {} min for direction {}", ttr.toMinutes(), dir);
      vertx.eventBus().publish("/config/update/ttr", config);
      vertx.setTimer(ttr.toMillis(), reset -> vertx.eventBus().send("dispatcher/" + dir + "/set", "", reply -> {
         if (reply.succeeded()) {
            vertx.eventBus().publish("/monitor", Instant.now() + " config reset");
            getDispatcherConfig(this::publishConfig);
         }
      }));
      ctx.response().setChunked(true).write("ok").end();
   }

   private void putConfig(final RoutingContext ctx) {

      JsonObject config = ctx.getBodyAsJson();
      LOG.info("Config change request: {}", config);

      String dir;
      if (config.containsKey("incoming")) {
         dir = "incoming";
      } else if (config.containsKey("outgoing")) {
         dir = "outgoing";
      } else {
         ctx.response().setStatusCode(400).setChunked(true).write("unknown config: " + config.toString()).end();
         return;
      }
      String handler = config.getString(dir);
      vertx.eventBus().send("dispatcher/" + dir + "/set", handler, reply -> {
         if (reply.succeeded()) {
            vertx.eventBus().publish("/monitor", Instant.now() + " config changed");
            getDispatcherConfig(cfg -> {
               respondConfig(ctx, cfg);
               publishConfig(cfg);
            });
         } else {
            ctx.response().setStatusCode(500).setChunked(true).write(reply.cause().getMessage()).end();
         }
      });

   }

   private void respondConfig(final RoutingContext ctx, final JsonObject cfg) {

      ctx.response().setChunked(true).putHeader("Content-Type", "application/json").write(cfg.toBuffer()).end();
   }

   private void publishConfig(final JsonObject cfg) {

      LOG.info("Publishing config update {}", cfg);
      vertx.eventBus().publish("/config/update", cfg);
   }

   private void getDispatcherConfig(Handler<JsonObject> configHandler) {

      vertx.eventBus().send("dispatcher/config", "", reply -> {
         if (reply.succeeded()) {
            JsonObject config = (JsonObject) reply.result().body();
            configHandler.handle(config);
         } else {
            LOG.warn("Retrieving config failed", reply.cause());
         }
      });
   }

}
