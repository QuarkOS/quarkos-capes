package dev.quarkos.capes.release;

import java.net.URI;

/**
 * Rejects a release service URL that is loopback, link-local, or a documentation name.
 * {@code gradle/release.gradle.kts} loads this class. Local parent builds pass {@code allowLocal}.
 */
public final class ReleaseServicePolicy {
    private ReleaseServicePolicy() {
    }

    public static void validate(String value, boolean allowLocal) {
        URI uri = null;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ignored) {
            uri = null;
        }
        boolean shaped = uri != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && uri.getHost() != null && !uri.getHost().isBlank()
                && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        if (!shaped) {
            throw new IllegalArgumentException("Invalid release serviceUrl: " + value);
        }
        String host = uri.getHost().toLowerCase().replaceFirst("^\\[", "").replaceFirst("]$", "").replaceAll("\\.+$", "");
        String[] labels = host.split("\\.");
        boolean ipv6 = host.indexOf(':') >= 0;
        boolean localAddress = false;
        if (ipv6) {
            try {
                var address = java.net.InetAddress.getByName(host);
                localAddress = address.isLoopbackAddress() || address.isAnyLocalAddress();
            } catch (Exception ignored) {
                localAddress = true;
            }
        }
        boolean local = localAddress || "localhost".equals(host) || host.endsWith(".localhost")
                || host.endsWith(".local") || "0.0.0.0".equals(host) || host.startsWith("127.")
                || host.matches("[0-9]+") || host.startsWith("0x");
        if (!local) {
            for (String label : labels) {
                if (label.length() > 1 && label.charAt(0) == '0' && label.chars().allMatch(Character::isDigit)) {
                    local = true;
                }
                if ("example".equals(label)) {
                    local = true;
                }
            }
        }
        if (!local && labels.length > 0) {
            String last = labels[labels.length - 1];
            if ("test".equals(last) || "invalid".equals(last)) {
                local = true;
            }
        }
        if (!allowLocal && local) {
            throw new IllegalArgumentException(
                    "Release jar has a loopback/example service URL (" + value + "). Set -PserviceUrl=<production URL>; "
                            + "use -PallowLocalService=true only for local parent validation.");
        }
    }
}
