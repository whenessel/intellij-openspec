package com.johnnyblabs.openspec.ai.backend;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelCatalogCacheTest {
    private static class MutableClock extends Clock {
        Instant value=Instant.EPOCH;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return value;}
    }
    private ModelCatalogSnapshot snapshot(Instant when) {return new ModelCatalogSnapshot(List.of(new ModelDescriptor("id","Model","",true)),when,false,true,"fresh");}
    private ModelCatalogCache.Key key(String executable,String auth,String account) {return new ModelCatalogCache.Key(executable,auth,account,"0.160.0");}
    @Test void ttlExpiresAtExactBoundaryAndClockRollbackDoesNotExtendFreshness() {
        MutableClock clock=new MutableClock();ModelCatalogCache cache=new ModelCatalogCache(clock,Duration.ofMinutes(5),2);var key=key("codex","chatgpt","account");cache.put(key,snapshot(clock.instant()));
        clock.value=Instant.EPOCH.plusSeconds(299);assertTrue(cache.fresh(key).isPresent());
        clock.value=Instant.EPOCH.plusSeconds(300);assertTrue(cache.fresh(key).isEmpty());assertTrue(cache.stale(key).isPresent());
        clock.value=Instant.EPOCH.minusSeconds(1);assertTrue(cache.fresh(key).isEmpty());
    }
    @Test void executableAndAccountScopeAreIsolatedAndAccountChangeInvalidatesOldScope() {
        ModelCatalogCache cache=new ModelCatalogCache(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),Duration.ofMinutes(5),3);
        ModelCatalogCache.Key first=key("codex","chatgpt","A"), second=key("codex","chatgpt","B"),other=key("other-codex","chatgpt","A");
        cache.put(first,snapshot(Instant.EPOCH));assertTrue(cache.stale(second).isEmpty());assertTrue(cache.stale(other).isEmpty());
        cache.put(second,snapshot(Instant.EPOCH));assertTrue(cache.stale(first).isEmpty());assertTrue(cache.stale(second).isPresent());
    }
    @Test void unidentifiedAccountsAndUnverifiedOrStaleDataAreNeverCached() {
        ModelCatalogCache cache=new ModelCatalogCache(Clock.systemUTC(),Duration.ofMinutes(5),2);var unidentified=key("codex","apikey","");
        cache.put(unidentified,snapshot(Instant.now()));assertTrue(cache.stale(unidentified).isEmpty());
        var key=key("codex","chatgpt","account");cache.put(key,new ModelCatalogSnapshot(List.of(),Instant.now(),true,true,"stale"));assertTrue(cache.stale(key).isEmpty());
        cache.put(key,new ModelCatalogSnapshot(List.of(),Instant.now(),false,false,"unknown"));assertTrue(cache.stale(key).isEmpty());
    }
    @Test void capacityAndExecutableInvalidationAreBounded() {
        ModelCatalogCache cache=new ModelCatalogCache(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),Duration.ofMinutes(5),2);
        ModelCatalogCache.Key first=key("first","chatgpt","A"),second=key("second","chatgpt","A"),third=key("third","chatgpt","A");
        cache.put(first,snapshot(Instant.EPOCH));cache.put(second,snapshot(Instant.EPOCH));cache.put(third,snapshot(Instant.EPOCH));
        assertTrue(cache.stale(first).isEmpty());cache.invalidateExecutable("second");assertTrue(cache.stale(second).isEmpty());assertTrue(cache.stale(third).isPresent());
    }
}
