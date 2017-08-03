package io.tourniquet.proxy.client;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Duration;

import io.tourniquet.proxy.MainProxyVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

@RunWith(VertxUnitRunner.class)
public class TestProxyClientIT {

   @Rule
   public RunTestOnContext rule = new RunTestOnContext();

   private static final Logger LOG = getLogger(TestProxyClientIT.class);

   private int configPort = 17099;
   private TestProxyClient client;

   @Before
   public void setUp(TestContext context) throws Exception {

      final JsonObject config = new JsonObject().put("proxyPort", 38080).put("configPort", configPort);

      rule.vertx().deployVerticle(MainProxyVerticle.class.getName(), new DeploymentOptions().setConfig(config), context.asyncAssertSuccess());

      this.client = new TestProxyClient("localhost", configPort);
   }

   @Test
   public void getConfig() throws Exception {

      Config config = client.getConfig();
      System.out.println(config);
   }

   @Test
   public void testSetConfig_for_incoming(TestContext context) throws Exception {

      Config config;
      try {
         config = client.getConfig();
      } catch (Exception e) {
         e.printStackTrace();
         throw new AssertionError(e);
      }

      String firstHandler = config.getHandlers().iterator().next();
      Config newConfig = client.setIncoming(firstHandler);

      context.assertEquals(firstHandler, newConfig.getIncoming());

   }

   @Test
   public void testSetConfig_for_outgoing(TestContext context) throws Exception {

      Config config;
      try {
         config = client.getConfig();
      } catch (Exception e) {
         e.printStackTrace();
         throw new AssertionError(e);
      }

      String firstHandler = config.getHandlers().iterator().next();
      Config newConfig = client.setOutgoing(firstHandler);

      context.assertEquals(firstHandler, newConfig.getOutgoing());
   }

   @Test
   public void testDisableIncoming(TestContext context) throws Exception {

      Config newConfig = client.setIncoming("");
      context.assertEquals("", newConfig.getIncoming());
   }

   @Test
   public void testDisableOutgoing(TestContext context) throws Exception {

      Config newConfig = client.setOutgoing("");
      context.assertEquals("", newConfig.getOutgoing());
   }

   @Test
   public void testSetTTRforIncoming(TestContext context) throws Exception {

      final Config config = client.getConfig();
      context.assertEquals("", config.getIncoming());

      final String firstHandler = config.getHandlers().iterator().next();
      final Config newConfig = client.setIncoming(firstHandler);
      context.assertEquals(firstHandler, newConfig.getIncoming());

      client.setTimeToReset(Direction.INCOMING, Duration.ofMinutes(1));

      Async done = context.async();

      rule.vertx().setTimer(62000, wakeup -> {
         Config cfg = client.getConfig();
         context.assertEquals("", cfg.getIncoming());
         done.complete();
      });
   }

}
