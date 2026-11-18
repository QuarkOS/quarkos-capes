package dev.quarkos.capes.client.net;

import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static dev.quarkos.capes.client.net.ServiceResponse.OfflineReason.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceResponseTest {
    private static final String CATALOG = "/v1/catalog";
    private static final String HEALTH = "/v1/health";
    private static final String JSON = "application/json; charset=utf-8";

    @Test void unavailableHttpStatuses() {
        assertEquals(NOT_FOUND, get(CATALOG, 404, "text/html", "<html>Not found</html>"));
        assertEquals(WRONG_SERVICE, get(HEALTH, 404, "text/html", "<html>Not found</html>"));
        assertEquals(SERVER_ERROR, get(CATALOG, 500, JSON, "{\"api\":1,\"error\":\"internal\"}"));
        assertEquals(RATE_LIMITED, get(CATALOG, 429, JSON, ""));
    }

    @Test void rejectsHtmlAndWrongOrMissingContentType() {
        assertEquals(CONTENT_TYPE, get(CATALOG, 200, "text/html", "<html>Wrong server</html>"));
        assertEquals(INVALID_JSON, get(CATALOG, 200, JSON, "<html>Wrong server</html>"));
        assertEquals(CONTENT_TYPE, get(CATALOG, 200, null, "{\"api\":1,\"items\":[]}"));
        assertEquals(CONTENT_TYPE, get(CATALOG, 200, "text/plain", "{\"api\":1,\"items\":[]}"));
    }

    @Test void validatesServiceIdentityAndRequiredFields() {
        assertNull(get("/prefix/v1/health", 200, JSON, "{\"api\":1,\"ok\":true}"));
        assertEquals(WRONG_SERVICE, get(HEALTH, 200, JSON, "{\"ok\":true}"));
        assertEquals(WRONG_SERVICE, get(HEALTH, 200, JSON, "{\"api\":1,\"ok\":false}"));
        assertNull(get(CATALOG, 200, JSON, "{\"api\":1,\"items\":[]}"));
        assertEquals(INVALID_RESPONSE, get(CATALOG, 200, JSON, "{\"api\":1}"));
        assertEquals(INVALID_RESPONSE, get(CATALOG, 200, JSON, "{\"api\":1,\"items\":[{}]}"));
        assertEquals(INVALID_RESPONSE, get(CATALOG, 200, JSON, "{\"api\":1,\"items\":[{\"source\":\"ours\",\"id\":\"cloak\"}]}"));
        assertEquals(INVALID_RESPONSE, get("/v1/players/wearing", 200, JSON, "{\"api\":1,\"players\":[{}]}"));
        assertEquals(INVALID_RESPONSE, get("/v1/me/wearing", 200, JSON, "{\"api\":1}"));
        assertNull(get("/v1/me/wearing", 200, JSON, "{\"api\":1,\"wearing\":null}"));
    }

    @Test void supportsApiErrorsAndBodylessProtocols() {
        assertNull(get("/v1/me/wearing", 401, JSON, "{\"api\":1,\"error\":\"signin\"}"));
        assertEquals(INVALID_RESPONSE, get(CATALOG, 204, null, ""));
        assertEquals(INVALID_RESPONSE, get(CATALOG, 304, null, ""));
        assertNull(ServiceResponse.classify("GET", CATALOG, 304, null, new byte[0], false, true));
        assertNull(ServiceResponse.classify("DELETE", "/v1/me/wearing", 204, null, new byte[0], false, false));
    }

    @Test void distinguishesTimeoutFromConnectionFailure() {
        assertEquals(TIMEOUT, ServiceResponse.transportFailure(new SocketTimeoutException()));
        assertEquals(TIMEOUT, ServiceResponse.transportFailure(new InterruptedIOException("timeout")));
        assertEquals(CONNECTION, ServiceResponse.transportFailure(new ConnectException()));
    }

    @Test void backsOffPerConfiguredUrlAndAllowsExplicitRetry() throws Exception {
        String base = "https://service.test";
        AtomicLong time = new AtomicLong();
        var backoff = new ServiceBackoff(time::get);
        var initial = backoff.before(base);
        backoff.failed(base, WRONG_SERVICE, "30", true);
        assertEquals(WRONG_SERVICE, backoff.reason(base));
        backoff.success(base, initial);
        assertEquals(WRONG_SERVICE, backoff.reason(base));
        backoff.before("https://service.test/added-prefix");
        time.set(29_999);
        assertThrows(CatalogClient.Failure.class, () -> backoff.before(base));
        time.set(30_000);
        assertTrue(backoff.before(base).healthProbe());
        backoff.failed(base, TIMEOUT, null, false);
        time.set(33_999);
        assertThrows(CatalogClient.Failure.class, () -> backoff.before(base));
        backoff.retry(base);
        backoff.success(base, backoff.before(base));
        assertNull(backoff.reason(base));
        backoff.failed(base, NOT_FOUND, "999999999999999999999", false);
        time.set(36_000);
        backoff.before(base);
    }

    private static ServiceResponse.OfflineReason get(String path, int status, String type, String body) {
        return ServiceResponse.classify("GET", path, status, type, body.getBytes(StandardCharsets.UTF_8), false, false);
    }
}
