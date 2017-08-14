package io.tourniquet.proxy;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 */
@RunWith(VertxUnitRunner.class)
public class DropDataVerticleTest {

    /**
     * The class under test
     */
    private DropDataVerticle verticle;

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();
    private EventBus eb;

    @Before
    public void setUp(TestContext context) throws Exception {
        verticle = new DropDataVerticle();
        rule.vertx().deployVerticle(verticle, context.asyncAssertSuccess());
        this.eb = rule.vertx().eventBus();
    }

    @Test
    public void identifier(TestContext context) throws Exception {
        context.assertEquals("drop", verticle.identifier());
    }

    @Test
    public void handleData(TestContext context) throws Exception {

        final Buffer buf = Buffer.buffer("testmessage");

        Async monitoring = context.async();
        Async data = context.async();

        eb.consumer("/monitor", msg -> {
            context.assertTrue(msg.body().toString().endsWith("Dropping data of length 11"));
            monitoring.complete();
        });

        eb.send("drop/data", buf, response -> {
            final Buffer empty = Buffer.buffer();
            context.assertEquals(empty, response.result().body());
            data.complete();
        });

    }

}
