package dev.quarkos.capes.client.net;

import java.net.URI;

/** Service roots must be HTTP(S) origins or paths, without credentials, queries or fragments. */
public final class ServiceUrl {
    private ServiceUrl() { }
    public static boolean valid(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && uri.getPort() >= -1 && uri.getPort() <= 65535;
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
