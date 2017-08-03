package io.tourniquet.proxy.client;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class ConfigTest {

   private Config config;

   @Before
   public void setUp() throws Exception {
      this.config = new Config("{\"incoming\":\"in\",\"outgoing\":\"out\",\"handlers\":[\"in\",\"out\",\"other\"]}");
   }

   @Test
   public void getIncoming() throws Exception {
      assertEquals("in", config.getIncoming());
   }

   @Test
   public void getOutgoing() throws Exception {
      assertEquals("out", config.getOutgoing());

   }

   @Test
   public void getHandlers() throws Exception {

      assertTrue(config.getHandlers().contains("in"));
      assertTrue(config.getHandlers().contains("out"));
      assertTrue(config.getHandlers().contains("other"));
   }

}
