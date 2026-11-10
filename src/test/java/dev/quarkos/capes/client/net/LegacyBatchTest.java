package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBatchTest {
    @Test void malformedLegacyModelsFailBeforeTheClientCallbackIsQueued() {
        String body="{\"api\":1,\"capes\":{\"00000000-0000-0000-0000-000000000001\":{\"status\":\"accepted\",\"model\":{\"fraction\":0.5}}}}";
        assertThrows(JsonValues.Bad.class,()->CapeHttp.batchMetadata(body));
        assertThrows(JsonValues.Bad.class,()->CapeHttp.batchMetadata("{\"api\":1,\"capes\":[]}"));
    }

    @Test void legacyDimensionsAndFrameArraysAreBoundedBeforeClientUse() {
        String body="{\"api\":1,\"capes\":{\"00000000-0000-0000-0000-000000000001\":{\"status\":\"accepted\",\"frameCount\":4294967296,\"frameWidth\":0,\"frameHeight\":999999,\"frameDelayMs\":[-1]},\"bad-uuid\":{\"status\":\"accepted\"}}}";
        var batch=CapeHttp.batchMetadata(body);
        assertEquals(1,batch.size());
        var meta=batch.values().iterator().next();
        assertEquals(256,meta.frameCount()); assertEquals(256,meta.delays().length);
        assertEquals(1,meta.frameWidth()); assertEquals(8192,meta.frameHeight()); assertEquals(50,meta.delays()[0]);
    }
}
