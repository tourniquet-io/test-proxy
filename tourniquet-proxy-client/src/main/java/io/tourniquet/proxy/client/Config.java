package io.tourniquet.proxy.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Config {

    private final String incoming;
    private final String outgoing;
    private final Set<String> handlers;

    public Config(final String jsonString) {

        this.incoming = resolve(jsonString, Pattern.compile("\"incoming\":\"([^\"]+)\""));
        this.outgoing = resolve(jsonString, Pattern.compile("\"outgoing\":\"([^\"]+)\""));
        this.handlers = resolveSet(jsonString, Pattern.compile("\"handlers\":\\[([^\\]]+)\\]"));
    }

    private Set<String> resolveSet(final String jsonString, final Pattern p) {

        return Arrays.stream(resolve(jsonString, p).split(","))
                     .filter(h -> h.matches("\"([^\"]+)\""))
                     .map(h -> h.substring(1, h.length() - 1))
                     .collect(Collectors.toSet());
    }

    private String resolve(final String jsonString, final Pattern p) {

        final Matcher m = p.matcher(jsonString);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    public String getIncoming() {

        return incoming;
    }

    public String getOutgoing() {

        return outgoing;
    }

    public Set<String> getHandlers() {

        return Collections.unmodifiableSet(handlers);
    }


    @Override
    public String toString() {

        final StringBuilder sb = new StringBuilder("Config{");
        sb.append("incoming='").append(incoming).append('\'');
        sb.append(", outgoing='").append(outgoing).append('\'');
        sb.append(", handlers=").append(handlers);
        sb.append('}');
        return sb.toString();
    }
}
