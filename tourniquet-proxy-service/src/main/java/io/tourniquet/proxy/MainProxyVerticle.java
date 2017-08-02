package io.tourniquet.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
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
        vertx.deployVerticle(new HttpProxyVerticle(), new DeploymentOptions().setConfig(config));
        vertx.deployVerticle(new ConfigVerticle(), new DeploymentOptions().setConfig(config));
        vertx.deployVerticle(new DataDispatcherVerticle());
        vertx.deployVerticle(new DropDataVerticle());

    }
}

