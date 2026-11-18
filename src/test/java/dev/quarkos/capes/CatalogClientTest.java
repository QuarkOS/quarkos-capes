package dev.quarkos.capes;

import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.sim.JsonValues;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogClientTest {
    private static String fixture(String name) throws Exception {
        try (var stream=CatalogClientTest.class.getResourceAsStream("/site/"+name+".json")) {
            return new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static CatalogClient.Reply reply(int status,String body,String etag) {
        return new CatalogClient.Reply(status,body.getBytes(StandardCharsets.UTF_8),etag,null);
    }
    @Test void catalogContractAndUnknownFields() throws Exception {
        var page=CatalogClient.page(fixture("catalog").replace("\"uses\":12","\"uses\":12,\"futureField\":{\"x\":true}"));
        assertEquals(1,page.items().size()); var item=page.items().getFirst();
        assertEquals("verdant-signal",item.id()); assertEquals("ours",item.source()); assertEquals(64,item.frameWidth());
        assertEquals("available",page.sources().get("labymod")); assertNull(page.nextCursor());
    }
    @Test void wearingAndNullPlayers() throws Exception {
        var worn=CatalogClient.wearing(fixture("wearing")); assertEquals("labymod",worn.source()); assertEquals("cloak",worn.kind());
        var players=CatalogClient.players(fixture("players")); assertEquals(2,players.size());
        assertNull(players.get(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertNull(CatalogClient.wearing("{\"api\":1,\"wearing\":null}"));
    }
    @Test void opaqueCursorAndAllFiltersEncoded() {
        var query=new CatalogClient.Query("ours","cloak","green & blue","newest","model","tag /","a+/=?",100);
        String path=query.path(); assertTrue(path.contains("q=green%20%26%20blue")); assertTrue(path.contains("cursor=a%2B%2F%3D%3F"));
        assertTrue(path.endsWith("limit=50")); assertTrue(path.contains("style=model&tag=tag%20%2F"));
    }
    @Test void detailIdentifierIsOnePathSegment() throws Exception {
        AtomicReference<String> url=new AtomicReference<>();
        var client=new CatalogClient((m,u,b,e,p)-> {
            url.set(u);
            return reply(200,"{\"source\":\"labymod\",\"id\":\"cloak:ab\",\"textureUrl\":\"https://texture.laby.net/ab.png\"}",null);
        });
        client.detail("http://local/","labymod","cloak:a/b ?"); assertEquals("http://local/v1/catalog/labymod/cloak%3Aa%2Fb%20%3F",url.get());
    }
    @Test void emptyPageStillContinues() {
        var page=CatalogClient.page("{\"api\":1,\"items\":[],\"nextCursor\":\"more+/=\"}");
        assertTrue(page.items().isEmpty()); assertEquals("more+/=",page.nextCursor());
    }
    @Test void etagAnd304ReuseOriginalBody() throws Exception {
        AtomicInteger calls=new AtomicInteger();String body=fixture("catalog");
        var client=new CatalogClient((m,u,b,e,p)-> { if(calls.getAndIncrement()==0) { assertNull(e);return reply(200,body,"\"hash\""); }
            assertEquals("\"hash\"",e);return reply(304,"",null); });
        var a=client.request("http://local","GET","/v1/catalog",null,null);var b=client.request("http://local","GET","/v1/catalog",null,null);
        assertEquals(a.text(),b.text());assertEquals(2,calls.get());
    }
    @Test void cachesAreSeparatedByOriginAndBearer() throws Exception {
        var client=new CatalogClient((m,u,b,e,p)->{assertNull(e);return reply(200,"{}","tag");});
        client.request("http://one","GET","/x","a",null);client.request("http://one","GET","/x","b",null);client.request("http://two","GET","/x","a",null);
    }
    @Test void chunksCoverEveryPlayerWithoutDuplicates() {
        List<UUID> ids=new ArrayList<>();for(int i=0;i<123;i++)ids.add(new UUID(0,i));ids.add(ids.getFirst());
        var chunks=CatalogClient.chunks(ids); assertEquals(List.of(50,50,23),chunks.stream().map(List::size).toList());
        assertEquals(ids.subList(0,123),chunks.stream().flatMap(Collection::stream).toList());assertTrue(CatalogClient.chunks(List.of()).isEmpty());
    }
    @Test void errorMapping() {
        assertEquals("invalid",CatalogClient.errorKey(400));assertEquals("conflict",CatalogClient.errorKey(409));
        assertEquals("signin",CatalogClient.errorKey(401));assertEquals("retry",CatalogClient.errorKey(429));assertEquals("error",CatalogClient.errorKey(503));
    }
    @Test void rateLimitBackoffHonorsRetryAfter() throws Exception {
        AtomicLong time=new AtomicLong(100);AtomicInteger calls=new AtomicInteger();
        var client=new CatalogClient((m,u,b,e,p)->{calls.incrementAndGet();return new CatalogClient.Reply(429,new byte[0],null,"30");},time::get);
        assertThrows(CatalogClient.Failure.class,()->client.request("http://local","GET","/x",null,null));
        time.set(30099);assertThrows(CatalogClient.Failure.class,()->client.request("http://local","GET","/x",null,null));assertEquals(1,calls.get());
        time.set(30100);assertThrows(CatalogClient.Failure.class,()->client.request("http://local","GET","/x",null,null));assertEquals(2,calls.get());
    }
    @Test void serverFailureBackoffAndRecovery() throws Exception {
        AtomicLong time=new AtomicLong(0);AtomicInteger calls=new AtomicInteger();
        var client=new CatalogClient((m,u,b,e,p)->reply(calls.getAndIncrement()==0?503:200,"{}",null),time::get);
        assertThrows(CatalogClient.Failure.class,()->client.request("http://local","GET","/x",null,null));
        assertThrows(CatalogClient.Failure.class,()->client.request("http://local","GET","/x",null,null));time.set(2001);
        assertEquals(200,client.request("http://local","GET","/x",null,null).status());assertEquals(2,calls.get());
    }
    @Test void concurrentGetsShareOneTransportCall() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        var client=new CatalogClient((m,u,b,e,p)->{calls.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return reply(200,"{}",null);});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->client.request("http://local","GET","/x",null,null));assertTrue(entered.await(5,TimeUnit.SECONDS));
            CountDownLatch secondStarted=new CountDownLatch(1);
            var second=executor.submit(()->{secondStarted.countDown();return client.request("http://local","GET","/x",null,null);});
            assertTrue(secondStarted.await(5,TimeUnit.SECONDS));
            // Wait until the second worker actually blocks on the shared future.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(!second.isDone() && System.nanoTime()<deadline && calls.get()==1) {
                if(Thread.getAllStackTraces().values().stream().anyMatch(stack->Arrays.stream(stack).anyMatch(f->f.getClassName().equals(CatalogClient.class.getName()) && f.getMethodName().equals("request") && f.getLineNumber()>0)
                    && Arrays.stream(stack).anyMatch(f->f.getClassName().contains("CompletableFuture")))) break;
                Thread.yield();
            }
            release.countDown();assertEquals(first.get().text(),second.get().text());assertEquals(1,calls.get());
        } finally {release.countDown();}
    }
    @Test void boundedFramesAndModelPreserved() {
        var item=CatalogClient.item(JsonValues.parse("{\"source\":\"ours\",\"id\":\"x\",\"frameCount\":99999,\"frameWidth\":-1,\"frameDelayMs\":[0],\"model\":{\"cubes\":[]}}"));
        assertEquals(256,item.delays().length);assertEquals(50,item.delays()[0]);assertEquals(1,item.frameWidth());assertEquals("{\"cubes\":[]}",item.modelJson());
    }
    @Test void writesSendJsonAndDoNotShareGetCache() throws Exception {
        AtomicReference<String> method=new AtomicReference<>();
        var client=new CatalogClient((m,u,b,e,p)->{method.set(m);assertEquals("bearer",b);assertNull(e);
            assertEquals("ours",JsonValues.object(JsonValues.parse(new String(p,StandardCharsets.UTF_8))).get("source"));return reply(200,fixture("wearing"),null);});
        client.request("http://local","PUT","/v1/me/wearing","bearer",Map.of("source","ours","id","x"));assertEquals("PUT",method.get());
    }
    @Test void unreachableTransportBacksOffExponentiallyAndRecovers() throws Exception {
        AtomicLong time = new AtomicLong(); AtomicInteger calls = new AtomicInteger();
        var client = new CatalogClient((m,u,b,e,p) -> {
            if (calls.incrementAndGet() < 3) throw new java.io.IOException("unreachable");
            return reply(200, "{}", null);
        }, time::get);
        assertThrows(java.io.IOException.class, () -> client.request("http://local", "GET", "/x", null, null));
        time.set(1999);
        assertThrows(CatalogClient.Failure.class, () -> client.request("http://local", "GET", "/x", null, null));
        assertEquals(1, calls.get());
        time.set(2000);
        assertThrows(java.io.IOException.class, () -> client.request("http://local", "GET", "/x", null, null));
        time.set(5999);
        assertThrows(CatalogClient.Failure.class, () -> client.request("http://local", "GET", "/x", null, null));
        assertEquals(2, calls.get());
        time.set(6000); assertEquals(200, client.request("http://local", "GET", "/x", null, null).status());
        assertEquals(200, client.request("http://local", "GET", "/x", null, null).status());
        assertEquals(4, calls.get());
    }
    @Test void malformedSuccessIsNotCachedAndUsesBackoff() throws Exception {
        for (String body : List.of("<html>proxy error</html>", "garbage", "[]", "{}", "{broken")) {
            AtomicLong time = new AtomicLong(); AtomicInteger calls = new AtomicInteger();
            var client = new CatalogClient((m,u,b,e,p) -> {
                assertNull(e);
                return reply(200, calls.incrementAndGet() == 1 ? body : "{\"items\":[]}", "bad-tag");
            }, time::get);
            assertThrows(CatalogClient.Failure.class, () -> client.page("http://local", new CatalogClient.Query("ours", "", "", "", "", "", "", 8)));
            assertThrows(CatalogClient.Failure.class, () -> client.page("http://local", new CatalogClient.Query("ours", "", "", "", "", "", "", 8)));
            assertEquals(1, calls.get()); time.set(2000);
            assertTrue(client.page("http://local", new CatalogClient.Query("ours", "", "", "", "", "", "", 8)).items().isEmpty());
            assertEquals(2, calls.get());
        }
    }
    @Test void invalidServiceUrlsKeepLastValidSetting() {
        var settings = dev.quarkos.capes.client.local.ClientSettings.get(); String previous = settings.baseUrl;
        try {
            for (String value : List.of("", "http://", "garbage", "file:///etc/config", "ftp://service.test", "https://user:secret@service.test", "https://service.test?x=1", "https://service.test/#fragment", "http://service.test:70000")) {
                dev.quarkos.capes.client.screen.CapeBoard.baseUrl(value);
                assertEquals(previous, settings.baseUrl, value);
            }
            dev.quarkos.capes.client.screen.CapeBoard.baseUrl(" https://service.test/api ");
            assertEquals("https://service.test/api", settings.baseUrl);
            assertTrue(dev.quarkos.capes.client.net.ServiceUrl.valid("http://127.0.0.1:8787"));
            assertTrue(dev.quarkos.capes.client.net.ServiceUrl.valid("http://[::1]:8787"));
        } finally { settings.baseUrl = previous; }
    }

    @Test void disabledOrUnavailableSourcesNeverProduceBrowsableTextures() {
        for (String status : List.of("disabled", "unavailable")) {
            var page = CatalogClient.page("{\"items\":[{\"source\":\"ours\",\"id\":\"local\"},{\"source\":\"labymod\",\"id\":\"cloak:abc\"}],"
                    + "\"nextCursor\":\"more\",\"sources\":{\"ours\":\"available\",\"labymod\":\"" + status + "\"}}");
            assertEquals(List.of("ours"), page.items().stream().map(CatalogClient.Item::source).toList());
            assertEquals("more", page.nextCursor());
            assertFalse(CatalogClient.sourceAvailable(page.sources(), "labymod"));
        }
        assertTrue(CatalogClient.sourceAvailable(Map.of(), "ours"));
    }

    @Test void serverDisableDoesNotFallBackToCachedDetail() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = new CatalogClient((m,u,b,e,p) -> calls.incrementAndGet() == 1
                ? reply(200, "{\"source\":\"labymod\",\"id\":\"cloak:abc\",\"textureUrl\":\"https://texture.laby.net/abc.png\"}", "tag")
                : reply(503, "{\"error\":\"source_disabled\"}", null));
        assertEquals("labymod", client.detail("http://local", "labymod", "cloak:abc").source());
        var failure = assertThrows(CatalogClient.Failure.class,
                () -> client.detail("http://local", "labymod", "cloak:abc"));
        assertEquals(503, failure.status);
        assertEquals(2, calls.get());
    }

}
