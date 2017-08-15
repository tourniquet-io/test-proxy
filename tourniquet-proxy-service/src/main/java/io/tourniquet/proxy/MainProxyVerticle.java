package io.tourniquet.proxy;

import static org.slf4j.LoggerFactory.getLogger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import org.slf4j.Logger;

public class MainProxyVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(MainProxyVerticle.class);

    public static void main(String... args) {

        // We set this property to prevent Vert.x caching files loaded from the classpath on disk
        // This means if you edit the static files in your IDE then the next time they are served the new ones will
        // be served without you having to restart the main()
        // This is only useful for development - do not use this in a production server
        System.setProperty("vertx.disableFileCaching", "true");

        String proxyHost = "localhost";
        int proxyPort = 8888;

        JsonObject config = new JsonObject().put("proxyPort", Integer.getInteger("proxyPort", 28080))
                                            .put("configPort", Integer.getInteger("configPort", 7099))
                                            .put("proxy",
                                                 new JsonObject().put("host", proxyHost).put("port", proxyPort))
                                            .put("httpConnector",
                                                 new HttpClientOptions().setMaxPoolSize(1024)
                                                                        .setKeepAlive(true)
                                                                        .setIdleTimeout(60)
                                                                        .setPipeliningLimit(1000)
                                                                        .setPipelining(true)
                                                                        .toJson());

        LOG.info("Debug enabled: {}", LOG.isDebugEnabled());
        LOG.debug("Trace enabled: {}", LOG.isTraceEnabled());
        LOG.trace("Starting Vert.x Event-Loop");

        MetricsOptions metricsOpts = new DropwizardMetricsOptions().setEnabled(true)
                                                                   .setJmxEnabled(true)
                                                                   .addMonitoredHttpClientEndpoint(new Match().setValue(
                                                                           proxyHost + ":" + proxyPort));

        Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(metricsOpts));
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
        vertx.deployVerticle(DataDispatcherVerticle.class.getName(),
                             complete -> vertx.deployVerticle(DropDataVerticle.class.getName(), handlerFuture));

        CompositeFuture.all(httpFuture, configFuture, handlerFuture).setHandler(complete -> {
            LOG.info("Test Proxy running");
            startFuture.complete();
        });
    }
}

