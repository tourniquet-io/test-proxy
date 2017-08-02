package io.tourniquet.proxy.client;

import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * A pure java client for configuring the test proxy to simplify automated tests.
 */
public class TestProxyClient {

    private final String hostname;
    private final int port;
    private final URI configUri;

    /**
     * Creates a new proxy client for the test proxy running it's configuration service at the specified port
     * @param hostname
     *  the hostname of the proxy
     * @param port
     *  the port of the configuration service
     */
    public TestProxyClient(final String hostname, final int port) {

        this.hostname = hostname;
        this.port = port;
        try {
            this.configUri = new URL("http", hostname, port, "/config").toURI();
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a client for a local test proxy, running at the default port
     */
    public TestProxyClient() {
        this("localhost",7099);
    }

    /**
     * Retrieves the current configuration of the proxy
     * @return
     */
    public Config getConfig(){

        try {
            final URL url = this.configUri.toURL();

            return new Config(readUrlConnection(url.openConnection()));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String readUrlConnection(final URLConnection con) throws IOException {

        try(InputStream in = con.getInputStream();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(in))) {
            return buffer.lines().collect(joining("\n"));
        }
    }

    /**
     * Convenience for setHandler(INCOMING, handler)
     * @param handler
     *  data handler for incoming connections
     * @return
     */
    public Config setIncoming(final String handler) {

        return setHandler(Direction.INCOMING, handler);

    }

    /**
     * Convenience for setHandler(OUTGOING, handler)
     * @param handler
     *  data handler for outgoing connections
     * @return
     */
    public Config setOutgoing(final String handler) {

        return setHandler(Direction.OUTGOING, handler);
    }

    /**
     * Sets a timer to reset the dataprocessing of the specified direction
     * @param dir
     *  the direction of data flow
     * @param dur
     *  the duration before the proxy is reset. Minimum resolution is 1 min.
     */
    public void setTimeToReset(Direction dir, Duration dur){

        try {
            postRequest(new URL(this.configUri.toURL() +"/ttr"), ()-> "{\"dir\":\""+dir+"\", \"ttr\": \""+dur.toMinutes()+"\"}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the name of the handler to process the data for the specified direction.
     * @param dir
     *  the direction of the data
     * @param handler
     *  the name of the handler to process the data
     * @return
     */
    public Config setHandler(final Direction dir, final String handler) {

        try {
            return new Config(putRequest(this.configUri.toURL(), () -> "{\""+dir+"\":\""+handler+"\"}"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String putRequest(final URL url, final Supplier<String> contentProvider) throws IOException {

        return sendData(url, "PUT", contentProvider);
    }

    private String postRequest(final URL url, final Supplier<String> contentProvider) throws IOException {

        return sendData(url, "POST", contentProvider);
    }

    private String sendData(final URL url, final String method, final Supplier<String> contentProvider) throws IOException {

        final HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod(method);
        try(OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream())) {
            out.write(contentProvider.get());
        }
        return readUrlConnection(httpCon);
    }
}
