package io.nexyo.edc.connector.edps.services.iam.did.web;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.UnaryOperator;


// TODO discuss if we get into problems because this code is just copied form an edc extension but is a private class.
public class WebDidUrlResolver implements UnaryOperator<String> {
    private static final String DID_SCHEME = "did";
    private static final String DID_METHOD_PREFIX = "web:";
    private static final String DID_DOCUMENT = "did.json";
    private static final String WELL_KNOWN = "/.well-known";
    private final String scheme;

    private final Monitor monitor;

    public WebDidUrlResolver(boolean useHttpsSchema, Monitor monitor) {
        this.scheme = useHttpsSchema ? "https" : "http";
        this.monitor = monitor;
    }

    public String apply(String did) {
        Objects.requireNonNull(did, "did");

        try {
            URL identifier = this.extractIdentifier(did);
            String url = identifier.toString();
            if (identifier.getPath().length() == 0) {
                url = url + "/.well-known";
            }

            return URLDecoder.decode(url + "/did.json", StandardCharsets.UTF_8);
        } catch (URISyntaxException | MalformedURLException var4) {
            throw new IllegalArgumentException(var4);
        }
    }

    private URL extractIdentifier(String did) throws URISyntaxException, MalformedURLException {
        URI uri = new URI(did);
        if (!"did".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid DID scheme: " + uri.getScheme());
        } else {
            String part = uri.getRawSchemeSpecificPart();
            if (!part.startsWith("web:")) {
                throw new IllegalArgumentException("Invalid DID format, the URN must specify the 'web' DID Method: " + did);
            } else if (part.endsWith(":")) {
                throw new IllegalArgumentException("Invalid DID format, the URN must not end with ':': " + did);
            } else {
                String host = part.substring("web:".length()).replace(':', '/');

                String decodedHost = null;
                try {
                    decodedHost = URLDecoder.decode(host, "UTF-8");
                    monitor.debug("##### decodedHost: " + decodedHost);
                } catch (Exception e) {
                    monitor.debug("##### UnsupportedEncodingException when trying to decode did url host: " + e.getMessage());
                    throw new EdcException("UnsupportedEncodingException when trying to decode did url host: " + e.getMessage());
                }


                return new URL(this.scheme + "://" + decodedHost);
            }
        }
    }
}
