package dev.quarkos.capes.client.net;

import java.net.URI;

/** Service roots must be HTTP(S) origins or paths, without credentials, queries or fragments. */
public final class ServiceUrl {
    private ServiceUrl() { }
    public static boolean sameOrigin(String first, String second) {
        if (first == null || second == null) return false;
        try {
            URI a = URI.create(first), b = URI.create(second);
            return a.getHost() != null && b.getHost() != null && a.getScheme() != null
                    && (a.getScheme().equalsIgnoreCase("http") || a.getScheme().equalsIgnoreCase("https"))
                    && a.getRawUserInfo() == null && b.getRawUserInfo() == null && a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost().equalsIgnoreCase(b.getHost()) && port(a) == port(b);
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }

    public static boolean insecure(String value) {
        if (!valid(value)) return false;
        URI uri = URI.create(value.trim());
        String host = uri.getHost();
        return "http".equalsIgnoreCase(uri.getScheme()) && !(host.equalsIgnoreCase("localhost")
                || host.equals("[::1]") || host.equals("127.0.0.1"));
    }
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
