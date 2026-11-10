package dev.quarkos.capes;

import dev.quarkos.capes.client.harness.HarnessMixins;
import dev.quarkos.capes.client.local.Samples;
import dev.quarkos.capes.client.net.ServiceUrl;
import dev.quarkos.capes.client.net.LabySource;
import dev.quarkos.capes.sim.ImageLimits;
import dev.quarkos.capes.sim.JsonValues;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Review08Test {
    @Test void pngLimitsRejectOversizedHeadersAndBodiesBeforeDecoding() {
        assertTrue(ImageLimits.png(Samples.bands()));
        assertFalse(ImageLimits.png(null));
        assertFalse(ImageLimits.png(new byte[24]));
        for (int[] size : new int[][]{{0,32},{-1,32},{8193,32},{8192,8192},{Integer.MAX_VALUE,2}}) {
            byte[] png = Samples.bands().clone();
            ByteBuffer.wrap(png).putInt(16,size[0]).putInt(20,size[1]);
            assertFalse(ImageLimits.png(png));
            assertNull(LabySource.present(png));
        }
        byte[] huge = java.util.Arrays.copyOf(Samples.bands(),ImageLimits.MAX_BYTES+1);
        assertFalse(ImageLimits.png(huge));
        byte[] wrongChunk = Samples.bands().clone(); wrongChunk[12]=0;
        assertFalse(ImageLimits.png(wrongChunk));
    }

    @Test void labyAtlasExpansionAndFrameCountAreBounded() throws Exception {
        for (int[] size : new int[][]{{2816,2176},{22,17*257}}) {
            var image = new java.awt.image.BufferedImage(size[0],size[1],java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image,"png",bytes);
            assertTrue(ImageLimits.png(bytes.toByteArray()));
            assertNull(LabySource.present(bytes.toByteArray()));
        }
    }

    @Test void jsonBombsFailWithOrdinaryValidationErrors() {
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("[".repeat(10000)+"0"+"]".repeat(10000)));
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("["+"0,".repeat(100001)+"0]"));
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("\""+"a".repeat(8*1024*1024)+"\""));
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("/* \" */["+"[".repeat(10000)+"0"+"]".repeat(10001)));
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("# \"\n"+"[".repeat(10000)+"0"+"]".repeat(10000)));
        assertThrows(JsonValues.Bad.class,()->JsonValues.parse("["+"0;".repeat(100001)+"0]"));
        String text="quotes \" \\ and [ { "+"[]".repeat(100);
        assertEquals(text,JsonValues.parse(JsonValues.write(text)));
    }

    @Test void jsonControlCharactersRoundTripAsEscapes() {
        String text="\u0000\b\f\n\r\t\u001f";
        String json=JsonValues.write(Map.of("code",text));
        assertEquals(text,JsonValues.object(JsonValues.parse(json)).get("code"));
        for (int i=0;i<json.length();i++) assertTrue(json.charAt(i)>=0x20);
    }

    @Test void originChecksIncludeSchemePortAndCredentials() {
        assertTrue(ServiceUrl.sameOrigin("https://SERVICE.test:443/a?q=1","https://service.test/root"));
        for (String url : new String[]{"http://service.test/a","https://service.test:444/a","https://other.test/a",
                "//service.test/a","https://user:password@service.test/a","broken"})
            assertFalse(ServiceUrl.sameOrigin(url,"https://service.test"),url);
        assertFalse(ServiceUrl.sameOrigin("https://service.test",null));
    }

    @Test void unencryptedRemoteServiceNeedsWarning() {
        assertTrue(ServiceUrl.insecure("http://service.test/api"));
        for (String url : new String[]{"https://service.test","http://localhost:8787","http://127.0.0.1:8787","http://[::1]:8787"})
            assertFalse(ServiceUrl.insecure(url),url);
    }

    @Test void mixinRequirementsTrackNamesRatherThanAConstantCount() {
        String config="{\"package\":\"dev.quarkos.capes.client.mixin\",\"client\":[\"CapeLayerMixin\",\"CapeGuiMixin\",\"ShotSkinMixin\",\"ShotCameraMixin\"],\"mixins\":[\"FutureMixin\"]}";
        String prefix="dev.quarkos.capes.client.mixin.";
        Set<String> expected=Set.of(prefix+"CapeLayerMixin",prefix+"CapeGuiMixin",prefix+"FutureMixin");
        assertEquals(expected,HarnessMixins.required(config));
        assertEquals(expected,HarnessMixins.missing(expected));
        HarnessMixins.applied(prefix+"CapeLayerMixin");
        HarnessMixins.applied(prefix+"UnrelatedMixin");
        HarnessMixins.applied(prefix+"ShotSkinMixin");
        assertEquals(Set.of(prefix+"CapeGuiMixin",prefix+"FutureMixin"),HarnessMixins.missing(expected));
    }
}
