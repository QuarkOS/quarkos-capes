package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;

import java.util.Map;

/** Pulls the image URL out of a provider JSON body. Image responses have no document. */
public final class ProviderDocs {
    public record Link(String url, boolean animated, int frames) {
    }

    private ProviderDocs() {
    }

    public static Link read(String provider, String body) {
        if ("minecraftcapes".equals(provider)) {
            Map<String, Object> root = JsonValues.object(JsonValues.parse(body));
            Object url = root.get("cape_url");
            if (!(url instanceof String s) || s.isBlank()) {
                return null;
            }
            boolean animated = root.get("animated_cape_url") instanceof String extra && !extra.isBlank();
            return new Link(s, animated, animated ? 0 : 1);
        }
        if ("cosmetica".equals(provider)) {
            Map<String, Object> root = JsonValues.object(JsonValues.parse(body));
            Object cloak = root.get("cloak");
            if (!(cloak instanceof Map<?, ?> raw)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            Object url = map.get("texture");
            if (!(url instanceof String s) || s.isBlank()) {
                return null;
            }
            int frames = map.get("frames") instanceof Long n ? n.intValue() : 1;
            return new Link(s, frames > 1, frames);
        }
        return null;
    }
}
