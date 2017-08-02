package io.tourniquet.proxy.client;

import static org.junit.Assert.assertEquals;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class TestProxyClientIT {

    private static final Logger LOG = getLogger(TestProxyClientIT.class);

    private int configPort = 17099;
    private TestProxyClient client;
    private Vertx vertx;

    @Before
    public void setUp() throws Exception {
//        JsonObject config = new JsonObject().put("proxyPort", 38080).put("configPort", configPort);
//
//        final CountDownLatch cdl = new CountDownLatch(4);
//
//        this.vertx = Vertx.vertx();
//        vertx.deployVerticle(new HttpProxyVerticle(), new DeploymentOptions().setConfig(config), r -> cdl.countDown());
//        vertx.deployVerticle(new ConfigVerticle(), new DeploymentOptions().setConfig(config), r -> cdl.countDown());
//        vertx.deployVerticle(new DataDispatcherVerticle(), r -> {
//            cdl.countDown();
//            vertx.deployVerticle(new DropDataVerticle(), r2 -> cdl.countDown());
//        });
//
//
//        assertTrue(cdl.await(20, TimeUnit.SECONDS));
//
//        LOG.info("Proxy Started");
//
//        this.client = new TestProxyClient("localhost", configPort);
        this.client = new TestProxyClient();
    }



    @After
    public void tearDown() throws Exception {
        final CountDownLatch cdl = new CountDownLatch(1);

        vertx.close(r -> cdl.countDown());
        cdl.await(20, TimeUnit.SECONDS);
    }

    @Test
    public void getConfig() throws Exception {

        Config config = client.getConfig();
        System.out.println(config);
    }

    @Test
    public void testSetConfig_for_incoming() throws Exception {
        Config config;
        try {
            config = client.getConfig();
        } catch (Exception e){
            e.printStackTrace();
            throw new AssertionError(e);
        }

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setIncoming(firstHandler);
        assertEquals(firstHandler, newConfig.getIncoming());

    }

    @Test
    public void testSetConfig_for_outgoing() throws Exception {
        Config config;
        try {
            config = client.getConfig();
        } catch (Exception e){
            e.printStackTrace();
            throw new AssertionError(e);
        }

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setOutgoing(firstHandler);
        assertEquals(firstHandler, newConfig.getOutgoing());
    }

    @Test
    public void testDisableIncoming() throws Exception {
        Config newConfig = client.setIncoming("");
        assertEquals("", newConfig.getIncoming());
    }


    @Test
    public void testDisableOutgoing() throws Exception {
        Config newConfig = client.setOutgoing("");
        assertEquals("", newConfig.getOutgoing());
    }

    @Test
    public void testSetTTRforIncoming() throws Exception {
        Config config = client.getConfig();
        assertEquals("", config.getIncoming());

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setIncoming(firstHandler);
        assertEquals(firstHandler, newConfig.getIncoming());

        client.setTimeToReset(Direction.INCOMING, Duration.ofMinutes(1));

        long now = System.currentTimeMillis();
        while(System.currentTimeMillis() - now < 65000){
            Thread.sleep(1000);
        }
        config = client.getConfig();
        assertEquals("", config.getIncoming());

    }

}
