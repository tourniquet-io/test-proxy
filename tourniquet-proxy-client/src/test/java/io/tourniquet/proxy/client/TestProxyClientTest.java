package io.tourniquet.proxy.client;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.Test;

public class TestProxyClientTest {

    @Test
    public void getConfig() throws Exception {

        //TODO startup proxy before test
        TestProxyClient client = new TestProxyClient();
        Config config = client.getConfig();
        System.out.println(config);
    }

    @Test
    public void testSetConfig_for_incoming() throws Exception {
        TestProxyClient client = new TestProxyClient();
        Config config = client.getConfig();

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setIncoming(firstHandler);
        assertEquals(firstHandler, newConfig.getIncoming());

    }

    @Test
    public void testSetConfig_for_outgoing() throws Exception {
        TestProxyClient client = new TestProxyClient();
        Config config = client.getConfig();

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setOutgoing(firstHandler);
        assertEquals(firstHandler, newConfig.getOutgoing());
    }

    @Test
    public void testDisableIncoming() throws Exception {
        TestProxyClient client = new TestProxyClient();

        Config newConfig = client.setIncoming("");
        assertEquals("", newConfig.getIncoming());
    }


    @Test
    public void testDisableOutgoing() throws Exception {
        TestProxyClient client = new TestProxyClient();

        Config newConfig = client.setOutgoing("");
        assertEquals("", newConfig.getOutgoing());
    }

    @Test
    public void testSetTTRforIncoming() throws Exception {
        TestProxyClient client = new TestProxyClient();
        Config config = client.getConfig();
        assertEquals("", config.getIncoming());

        String firstHandler = config.getHandlers().iterator().next();
        Config newConfig = client.setIncoming(firstHandler);
        assertEquals(firstHandler, newConfig.getIncoming());

        client.setTimeToReset(Direction.INCOMING, Duration.ofMinutes(1));

        Thread.sleep(65_1000);

        config = client.getConfig();
        assertEquals("", config.getIncoming());

    }

}
