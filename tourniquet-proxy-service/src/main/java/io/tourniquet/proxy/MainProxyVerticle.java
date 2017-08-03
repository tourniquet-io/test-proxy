package io.tourniquet.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class MainProxyVerticle extends AbstractVerticle {

   public static void main(String... args) {

      // We set this property to prevent Vert.x caching files loaded from the classpath on disk
      // This means if you edit the static files in your IDE then the next time they are served the new ones will
      // be served without you having to restart the main()
      // This is only useful for development - do not use this in a production server
      System.setProperty("vertx.disableFileCaching", "true");

      JsonObject config = new JsonObject().put("proxyPort", 28080).put("configPort", 7099);

      Vertx vertx = Vertx.vertx();
      vertx.deployVerticle(MainProxyVerticle.class.getName(), new DeploymentOptions().setConfig(config));

   }

   @Override
   public void start(final Future<Void> startFuture) throws Exception {

      final JsonObject config = config();

      final Future<String> httpFuture = Future.future();
      final Future<String> configFuture = Future.future();
      final Future<String> handlerFuture = Future.future();

      vertx.deployVerticle(HttpProxyVerticle.class.getName(), new DeploymentOptions().setConfig(config), httpFuture);
      vertx.deployVerticle(ConfigVerticle.class.getName(), new DeploymentOptions().setConfig(config), configFuture);
      vertx.deployVerticle(DataDispatcherVerticle.class.getName(), complete -> vertx.deployVerticle(DropDataVerticle.class.getName(), handlerFuture));

      CompositeFuture.all(httpFuture,configFuture,handlerFuture).setHandler(complete -> startFuture.complete());
   }
}

