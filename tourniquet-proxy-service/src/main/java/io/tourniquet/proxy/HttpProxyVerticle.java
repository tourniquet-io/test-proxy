package io.tourniquet.proxy;

import static io.tourniquet.proxy.TransmissionDirection.RCV;
import static io.tourniquet.proxy.TransmissionDirection.SND;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
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
import io.vertx.ext.dropwizard.MetricsService;

/**
 * Created on 26.06.2017.
 */
public class HttpProxyVerticle extends AbstractVerticle {

    public static final int HTTP_DEFAULT_PORT = 80;
    private static final org.slf4j.Logger LOG = getLogger(HttpProxyVerticle.class);
    /**
     * Global tracker for request IDs. Even if multiple proxy verticles are deployed, the request number is globally
     * unique (inside a jvm)
     */
    private static final AtomicInteger REQUEST_ID = new AtomicInteger(0);
    /**
     * HTTP client for non-https connections
     */
    private HttpClient httpClient;
    /**
     * TCP client for HTTPs connections
     */
    private NetClient netClient;
    private HttpServerOptions httpOpts;
    /**
     * The Proxy server
     */
    private HttpServer httpServer;
    private Statistics stats;
    private Optional<HostInfo> extProxy;

    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        final JsonObject config = config();

        this.netClient = vertx.createNetClient();

        if (config.containsKey("proxy")) {
            final JsonObject proxyConfig = config.getJsonObject("proxy");
            LOG.info("Using external proxy on {}:{}", proxyConfig.getString("host"), proxyConfig.getInteger("port"));
            this.extProxy = Optional.of(HostInfo.from(proxyConfig.getString("host"), proxyConfig.getInteger("port")));
        } else {
            this.extProxy = Optional.empty();
        }

        final HttpClientOptions clientOpts;
        if (config.containsKey("httpConnector")) {
            clientOpts = new HttpClientOptions(config.getJsonObject("httpConnector"));
        } else {
            clientOpts = new HttpClientOptions().setMaxPoolSize(1024)
                                                .setKeepAlive(true)
                                                .setIdleTimeout(60)
                                                .setPipeliningLimit(1000)
                                                .setPipelining(true);
        }
        this.httpClient = vertx.createHttpClient(clientOpts);

        final int httpPort = config.getInteger("proxyPort", 28080);
        this.httpOpts = new HttpServerOptions().setPort(httpPort);
        this.httpServer = vertx.createHttpServer(httpOpts).requestHandler(this::processRequest).listen(result -> {
            LOG.info("Proxy startup complete, Proxy listening on {}", httpPort);
            startFuture.complete();
        });

        this.stats = new Statistics();

        vertx.eventBus().consumer("/stats/get", msg -> msg.reply(this.stats.toJsonObject()));
        vertx.setPeriodic(1000, v -> vertx.eventBus().publish("/stats", this.stats.toJsonObject()));
      vertx.setPeriodic(10000, v -> {
         MetricsService metricsService = MetricsService.create(vertx);
         JsonObject netsockets = metricsService.getMetricsSnapshot("vertx.http.clients.open-netsockets");
         System.out.println(netsockets);
         System.out.println(metricsService.getMetricsSnapshot("vertx.http"));

         if (netsockets.getJsonObject("vertx.http.clients.open-netsockets").getInteger("count") == clientOpts.getMaxPoolSize()) {
            LOG.warn("HttpClient reached connection pool size limit {}", clientOpts.getMaxPoolSize());
            restartHttpClient(clientOpts);
         }
      });

   }

   private void restartHttpClient(final HttpClientOptions clientOpts) {

      LOG.info("Restarting HTTP Client");
      httpClient.close();
      httpClient = vertx.createHttpClient(clientOpts);
      LOG.info("HTTP Client restarted");

    }

    private void processRequest(final HttpServerRequest req) {

        this.stats.totalRequests++;
        final int conId = REQUEST_ID.incrementAndGet();
        LOG.info("[{}] Incoming Proxy request: {} {} {} {}",
                 conId,
                 req.isSSL() ? "HTTPS" : "HTTP",
                 req.method(),
                 req.uri(),
                 req.version());

        req.exceptionHandler(e -> LOG.error("Exception while processing response", e.getCause()));

        if (req.method() == HttpMethod.CONNECT) {
            httpsPassthrough(conId, req, HostInfo.from(req.uri(), 433));
        } else {
            httpPassthrough(conId, req, HostInfo.from(req.getHeader("Host"), HTTP_DEFAULT_PORT));
        }
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

        if (extProxy.isPresent()) {
            final HostInfo proxyInfo = extProxy.get();
            LOG.debug("[{}] Forwarding proxy request", conId);
            req.headers().forEach(e -> LOG.info("[{}] {} -> {}", conId, e.getKey(), e.getValue()));
            final HttpClientRequest proxyRequest = httpClient.request(HttpMethod.CONNECT,
                                                                      proxyInfo.port,
                                                                      proxyInfo.host,
                                                                      info.host + ":" + info.port);
            proxyRequest.handler(resp -> {
            LOG.info("[{}] {} {}", conId, resp.statusCode(), resp.statusMessage());
                if (resp.statusCode() == 200) {
                    socketPassthrough(conId, req, resp.netSocket()).setHandler(v -> {
                        req.netSocket().close();
                        resp.netSocket().close();
                        LOG.debug("[{}] Client and Server sockets closed", conId);
                    });
                } else {
                    LOG.error("[{}] proxy connection failed", conId);
                    req.response().setStatusCode(resp.statusCode()).setStatusMessage(resp.statusMessage()).end();
                }
            });
            proxyRequest.exceptionHandler(e -> LOG.error("[{}] proxy connection failed", conId, e.getCause()));
            proxyRequest.headers().setAll(req.headers());
            proxyRequest.end();

        } else {
            LOG.debug("[{}] Dispatching https request {} -> {}", conId, req.absoluteURI(), info);
            netClient.connect(info.port, info.host, resp -> {
                if (resp.succeeded()) {
                    socketPassthrough(conId, req, resp.result()).setHandler(v -> {
                        handleClose(conId, resp.result());
                        handleClose(conId, req.netSocket());
                    });
                } else {
                    LOG.error("[{}] Connecting remote host failed", conId, resp.cause());
                    req.response().setStatusCode(502).setStatusMessage("Could not connect to remote host").end();
                }
            });
        }
    }

    /**
     * Handles the socketPassthrough of non-encrypted http request.
     *
     * @param conId
     *         the current request id
     * @param req
     *         the incoming client request
     * @param info
     *         the host info of the remote target server.
     */
    private void httpPassthrough(final int conId, final HttpServerRequest req, final HostInfo info) {

        final HostInfo target = extProxy.orElse(info);

        if (LOG.isTraceEnabled()) {
            LOG.trace("[{}] Sending request to destination {}\n<HEADER>\n{}\n</HEADER>",
                      conId,
                      info,
                      toString(req.headers()));
        }
      final HttpClientRequest outgoingRequest = this.httpClient.request(req.method(), target.port, target.host, req.uri());
        outgoingRequest.setChunked(true);
        outgoingRequest.exceptionHandler(e -> LOG.error("Exception while processing request", e.getCause()));
        outgoingRequest.handler(targetResponse -> processServerResponse(conId, req.response(), targetResponse));
        outgoingRequest.headers().setAll(req.headers());

      final ChunkDataHandler requestHandler = new ChunkDataHandler(conId, SND, outgoingRequest).closeHandler(v -> outgoingRequest.end());

        req.handler(requestHandler);
        req.endHandler(v -> requestHandler.endTransmission());
    }

    /**
     * Passes through all binary data from the client to the destination socket.
     *
     * @param conId
     *         the current connection id
     * @param clientRequest
     *         the initial client request. A response is sent to the client to confirm the connection has been
     *         established. All further binary data is streamed directly to the destination socket.
     * @param server
     */
    private CompositeFuture socketPassthrough(final int conId,
                                              final HttpServerRequest clientRequest,
                                              final NetSocket server) {

        confirmProxyHttpsConnection(conId, clientRequest);

        final NetSocket client = clientRequest.netSocket();

        final Future<Void> srcClosed = connectSockets(conId, client, server, SND);
        final Future<Void> dstClosed = connectSockets(conId, server, client, RCV);

        return CompositeFuture.all(srcClosed, dstClosed);
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

    private void processServerResponse(final int conId,
                                       final HttpServerResponse respToClient,
                                       final HttpClientResponse respFromServer) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("[{}] Received response {} {},\n-HEADER-\n{}\n-HEADER-",
                      conId,
                      respFromServer.statusCode(),
                      respFromServer.statusMessage(),
                      toString(respFromServer.headers()));
        }

      final ChunkDataHandler responseHandler = new ChunkDataHandler(conId, RCV, respToClient).closeHandler(v -> respToClient.end());
        respToClient.setChunked(true);
        respToClient.setStatusCode(respFromServer.statusCode());
        respToClient.headers().setAll(respFromServer.headers());
        respFromServer.handler(responseHandler);
        respFromServer.endHandler(v -> responseHandler.endTransmission());
    }

    /**
     * Sends confirmation to the client that https connection could be established
     *
     * @param conId
     *         the connection/request id
     * @param req
     */
    private void confirmProxyHttpsConnection(final int conId, final HttpServerRequest req) {

        LOG.debug("[{}] Confirming Proxy Connection", conId);
        req.response()
           .setStatusCode(200)
           .setStatusMessage("Connection established")
           .putHeader("Proxy-agent", "Test-Proxy 1.0")
           .end();
    }

    private Future<Void> connectSockets(final int conId,
                                        final NetSocket srcSocket,
                                        final NetSocket dstSocket,
                                        final TransmissionDirection dir) {

        final Future<Void> srcClosed = Future.future();
        final ChunkDataHandler dataHandler = new ChunkDataHandler(conId, dir, dstSocket);
        srcSocket.handler(dataHandler);
        srcSocket.exceptionHandler(e -> LOG.error("Error on socket", e.getCause()));
        srcSocket.endHandler(v -> {
            LOG.debug("[{}] Socket closed by client", conId);
            dataHandler.endTransmission();
            srcClosed.complete();
        });
        return srcClosed;
    }

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

    /**
     * Data handler for processing chunks of data by passing them to a handler on the eventbus. The data handler
     * keeps track of in-flight chunks on the event bus
     */
    private class ChunkDataHandler implements Handler<Buffer> {

        private final int conId;
        private final TransmissionDirection transmissionDirection;
        private final WriteStream<Buffer> out;
        private int dataInFlight = 0;
        private boolean indicateEnd;
        private Handler<Void> onCloseHandler;
        private boolean closed = false;

        public ChunkDataHandler(final int conId,
                                final TransmissionDirection transmissionDirection,
                                final WriteStream<Buffer> out) {

            this.conId = conId;
            this.transmissionDirection = transmissionDirection;
            this.out = out;

        }

        @Override
        public void handle(final Buffer data) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}] {}: {} bytes\n---\n{}\n---",
                          conId,
                          transmissionDirection,
                          data.length(),
                          data.toString(Charset.defaultCharset()));
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("[{}] {}: {} bytes", conId, transmissionDirection, data.length());
            }

            stats.receivedBytes(transmissionDirection, data.length());
            dataInFlight++;

            Vertx.currentContext()
                 .owner()
                 .eventBus()
                 .send(transmissionDirection.getAddress(), data, transmitData(data));
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

            if (!closed) {
                LOG.debug("[{}] finalize transmission", conId);

                closed = true;
                if (onCloseHandler != null) {
                    onCloseHandler.handle(null);
                }
            }
        }

        /**
         * Indicate the transmission is complete. The method finishes the writing to the stream, if there is no data in
         * processing that needs to be completed.
         */
        public void endTransmission() {

            if (dataInFlight == 0) {
                finalizeTransmission();
            } else {
                LOG.debug("[{}] mark end transmission ", this.conId);
                indicateEnd = true;
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
        public ChunkDataHandler closeHandler(final Handler<Void> onCloseHandler) {

            this.onCloseHandler = onCloseHandler;
            return this;
        }
    }

}
