package io.tourniquet.proxy;

import static io.tourniquet.proxy.TransmissionDirection.RCV;
import static io.tourniquet.proxy.TransmissionDirection.SND;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.WriteStream;

/**
 * Created on 26.06.2017.
 */
public class HttpProxyVerticle extends AbstractVerticle {

   private static final org.slf4j.Logger LOG = getLogger(HttpProxyVerticle.class);
   public static final int HTTP_DEFAULT_PORT = 80;

   /**
    * HTTP client for non-https connections
    */
   private HttpClient httpClient;
   /**
    * TCP client for HTTPs connections
    */
   private NetClient netClient;

   /**
    * Global tracker for request IDs. Even if multiple proxy verticles are deployed, the request number is globally
    * unique (inside a jvm)
    */
   private static final AtomicInteger REQUEST_ID = new AtomicInteger(0);

   private HttpServerOptions httpOpts;
   /**
    * The Proxy server
    */
   private HttpServer httpServer;
   private Statistics stats;

   @Override
   public void start(final Future<Void> startFuture) throws Exception {

      JsonObject config = config();
      this.netClient = vertx.createNetClient();
      this.httpClient = vertx.createHttpClient(new HttpClientOptions());

      this.httpOpts = new HttpServerOptions().setPort(config.getInteger("proxyPort", 28080));
      this.httpServer = vertx.createHttpServer(httpOpts).requestHandler(this::processRequest).listen(result -> {
         LOG.info("Proxy startup complete");
         startFuture.complete();
      });

      this.stats = new Statistics();

      vertx.eventBus().consumer("/stats/get", msg -> msg.reply(this.stats.toJsonObject()));
      vertx.setPeriodic(1000, v -> vertx.eventBus().publish("/stats", this.stats.toJsonObject()));
   }

   private void processRequest(final HttpServerRequest req) {

      this.stats.totalRequests++;
      final int conId = REQUEST_ID.incrementAndGet();
      LOG.info("[{}] Incoming Proxy request: {} {} {} {}", conId, req.isSSL() ? "HTTPS" : "HTTP", req.method(), req.uri(), req.version());

      if (req.method() == HttpMethod.CONNECT) {
         HostInfo info = HostInfo.from(req.uri(), 433);
         httpsPassthrough(conId, req, info);
      } else {
         final HostInfo info = HostInfo.from(req.getHeader("Host"), HTTP_DEFAULT_PORT);
         if (LOG.isTraceEnabled()) {
            LOG.trace("[{}] Sending request to destination {}\n-HEADER-\n{}\n-HEADER-", conId, info, toString(req.headers()));
         }
         final HttpClientRequest outgoingRequest = this.httpClient.request(req.method(),
                                                                           info.port,
                                                                           info.host,
                                                                           req.uri(),
                                                                           targetResponse -> processServerResponse(conId, req.response(), targetResponse)).setChunked(true);
         outgoingRequest.headers().setAll(req.headers());

         final ChunkDataHandler requestHandler = new ChunkDataHandler(conId, SND, req.response());
         req.handler(requestHandler);
         req.endHandler((v) -> requestHandler.endTransmission());
      }
   }

   private void processServerResponse(final int conId, final HttpServerResponse clientResponse, final HttpClientResponse serverResponse) {

      if (LOG.isTraceEnabled()) {
         LOG.trace("[{}] Received response {} {},\n-HEADER-\n{}\n-HEADER-", conId, serverResponse.statusCode(), serverResponse.statusMessage(), toString(serverResponse.headers()));
      }

      final ChunkDataHandler responseHandler = new ChunkDataHandler(conId, RCV, clientResponse);
      clientResponse.setChunked(true);
      clientResponse.setStatusCode(serverResponse.statusCode());
      clientResponse.headers().setAll(serverResponse.headers());
      serverResponse.handler(responseHandler);
      serverResponse.endHandler((v) -> responseHandler.endTransmission());
   }

   private String toString(final MultiMap headers) {

      final StringBuilder buf = new StringBuilder(128);
      boolean first = true;
      for (Map.Entry<String, String> entry : headers.entries()) {
         if (first) {
            first = false;
         } else {
            buf.append('\n');
         }
         buf.append(entry.getKey()).append(':').append(entry.getValue());
      }
      return buf.toString();
   }

   /**
    * Handles the request by establishing a end-2-end https connection without breaking the security of the connection.
    *
    * @param conId
    *         the current request id
    * @param req
    *         the incoming client request
    * @param info
    *         the information of the target host
    */
   private void httpsPassthrough(final int conId, final HttpServerRequest req, final HostInfo info) {

      netClient.connect(info.port, info.host, result -> {

         if (result.succeeded()) {

            confirmProxyHttpsConnection(conId, req);

            final NetSocket src = req.netSocket();
            final NetSocket dst = result.result();

            final ChunkDataHandler srcHandler = new ChunkDataHandler(conId, SND, dst).onClose(() -> handleClose(conId, dst));
            final ChunkDataHandler dstHandler = new ChunkDataHandler(conId, RCV, src).onClose(() -> handleClose(conId, src));

            src.handler(srcHandler);
            dst.handler(dstHandler);

            src.closeHandler(v -> {
               LOG.debug("[{}] Connection closed by client", conId);
               srcHandler.endTransmission();
            });
            dst.closeHandler(v -> {
               LOG.debug("[{}] Connection closed by server", conId);
               dstHandler.endTransmission();
            });

         } else {
            LOG.warn("[{}] Connecting remote host failed", conId, result.cause());
         }
      });
   }

   /**
    * Sends confirmation to the client that https connection could be established
    *
    * @param conId
    *         the connection/request id
    * @param req
    */
   private void confirmProxyHttpsConnection(final int conId, final HttpServerRequest req) {

      LOG.info("[{}] Confirming Proxy Connection", conId);
      req.response().setStatusCode(200).setStatusMessage("Connection established").putHeader("Proxy-agent", "Test-Proxy 1.0").end();
   }

   private <T> Handler<AsyncResult<T>> handleClose(final int conId, final NetSocket socket) {

      return (v) -> {
         socket.close();
         LOG.debug("[{}] connection closed", conId);
      };
   }

   /**
    * Helper class to parse hostname and port from a string hostname:port with the option to define a default port
    * during parsing
    */
   private static class HostInfo {

      final String host;
      final int port;

      HostInfo(final String host, final int port) {

         this.host = host;
         this.port = port;
      }

      static HostInfo from(String host, int defaultPort) {

         final int portSeparator = host.lastIndexOf(':');
         final int port = portSeparator == -1 ? defaultPort : Integer.parseInt(host.substring(portSeparator + 1));
         final String hostname = portSeparator == -1 ? host : host.substring(0, portSeparator);
         return new HostInfo(hostname, port);
      }

      @Override
      public String toString() {

         return host + ':' + port;
      }
   }

   private class ChunkDataHandler implements Handler<Buffer> {

      private final int conId;
      private final TransmissionDirection transmissionDirection;
      private final WriteStream<Buffer> out;
      private int dataInFlight = 0;
      private boolean indicateEnd;
      private Runnable onCloseHandler;

      public ChunkDataHandler(final int conId, final TransmissionDirection transmissionDirection, final WriteStream<Buffer> out) {

         this.conId = conId;
         this.transmissionDirection = transmissionDirection;
         this.out = out;

      }

      @Override
      public void handle(final Buffer data) {

         stats.receivedBytes(transmissionDirection, data.length());

         if (LOG.isTraceEnabled()) {
            LOG.trace("[{}] {}: {} bytes\n---\n{}\n---", conId, transmissionDirection, data.length(), data.toString(Charset.defaultCharset()));
         } else if (LOG.isDebugEnabled()) {
            LOG.debug("[{}] {}: {} bytes", conId, transmissionDirection, data.length());
         }

         dataInFlight++;

         Vertx.currentContext().owner().eventBus().send(transmissionDirection.getAddress(), data, transmitData(data));
      }

      /**
       * Indicate the transmission is complete. The method finishes the writing to the stream, if there is no data in
       * processing that needs to be completed.
       */
      public void endTransmission() {

         if (dataInFlight == 0) {
            finalizeTransmission();
         } else {
            indicateEnd = true;
         }
      }

      /**
       * Transmit data that has been potentially modified.
       *
       * @param originalData
       *         original data. If the data modification failed, the original data ist sent
       *
       * @return the handler to accept the modified data
       */
      private Handler<AsyncResult<Message<Buffer>>> transmitData(final Buffer originalData) {

         return reply -> {
            if (reply.succeeded()) {
               final Buffer modifiedData = reply.result().body();
               if (modifiedData.length() > 0) {
                  LOG.debug("[{}] writing {} bytes", conId, modifiedData.length());
                  stats.transmitBytes(transmissionDirection, modifiedData.length());
                  out.write(modifiedData);
               } else {
                  stats.dropBytes(transmissionDirection, originalData.length());
                  LOG.debug("[{}] writing no data", conId);
               }
            } else {
               LOG.debug("[{}] Dataprocessing failed", conId, reply.cause());
               stats.transmitBytes(transmissionDirection, originalData.length());
               out.write(originalData);
            }
            dataInFlight--;
            if (indicateEnd && dataInFlight == 0) {
               finalizeTransmission();
            }
         };
      }

      private void finalizeTransmission() {

         out.end();
         if (onCloseHandler != null) {
            onCloseHandler.run();
         }
      }

      /**
       * Sets a handler that is invoked after the final data has been written and the WriteStream been ended
       *
       * @param onCloseHandler
       *         the handler to execute
       *
       * @return
       */
      public ChunkDataHandler onClose(final Runnable onCloseHandler) {

         this.onCloseHandler = onCloseHandler;
         return this;
      }
   }

   static class Statistics {

      int totalRequests;
      int requestBytes;
      int responseBytes;
      int requestBytesTransmitted;
      int responseBytesTransmitted;
      int requestBytesDropped;
      int responseBytesDropped;

      void dropBytes(TransmissionDirection dir, int numBytes) {

         if (dir == SND) {
            requestBytesDropped += numBytes;
         } else {
            responseBytesDropped += numBytes;
         }
      }

      void receivedBytes(TransmissionDirection dir, int numBytes) {

         if (dir == SND) {
            requestBytes += numBytes;
         } else {
            responseBytes += numBytes;
         }
      }

      void transmitBytes(TransmissionDirection dir, int numBytes) {

         if (dir == SND) {
            requestBytesTransmitted += numBytes;
         } else {
            responseBytesTransmitted += numBytes;
         }
      }

      public JsonObject toJsonObject() {

         return new JsonObject().put("totalRequests", totalRequests)
                                .put("requestBytes", requestBytes)
                                .put("responseBytes", responseBytes)
                                .put("requestBytesTransmitted", requestBytesTransmitted)
                                .put("responseBytesTransmitted", responseBytesTransmitted)
                                .put("requestBytesDropped", requestBytesDropped)
                                .put("responseBytesDropped", responseBytesDropped);
      }

   }

}
