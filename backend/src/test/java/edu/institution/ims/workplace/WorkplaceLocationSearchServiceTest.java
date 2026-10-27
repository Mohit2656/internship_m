package edu.institution.ims.workplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WorkplaceLocationSearchServiceTest {
    private static final String PLACE = """
            {"features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[79.0875,21.125]},
              "properties":{"name":"Actual branch","housenumber":"12","street":"College Road","city":"Nagpur","state":"Maharashtra","country":"India","osm_id":123}}]}
            """;
    private final AtomicLong nanos = new AtomicLong();
    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer upstream;
    private WorkplaceLocationSearchService service;

    @BeforeEach void setUp() {
        var builder = RestClient.builder().baseUrl("https://photon.example");
        upstream = MockRestServiceServer.bindTo(builder).build();
        service = new WorkplaceLocationSearchService(builder.build(), clock, nanos::get);
    }

    @Test void searchesFixedProviderWithEncodedQueryAndMapsLongitudeBeforeLatitude() {
        upstream.expect(requestTo("https://photon.example/api/?q=R%26D%20%2B%20Labs&limit=5&lang=en"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        var results = service.search("  R&D  + Labs  ");
        assertThat(results).containsExactly(new WorkplaceLocationSearchResult("Actual branch",
                "Actual branch, 12, College Road, Nagpur, Maharashtra, India", 21.125, 79.0875));
        upstream.verify();
    }

    @Test void invalidQueriesNeverReachProvider() {
        for (String query : new String[]{null, "", "  ", "ab", "x".repeat(201)}) {
            assertThatThrownBy(() -> service.search(query)).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                    error -> { assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST); assertThat(error.code()).isEqualTo("INVALID_WORKPLACE_SEARCH"); });
        }
        upstream.verify();
    }

    @Test void normalizedCacheHitsBypassGlobalRateLimitAndExpireAfterTwentyFourHours() {
        upstream.expect(times(2), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        var first = service.search("Actual  Branch");
        assertThat(service.search(" ACTUAL branch ")).isSameAs(first);
        clock.instant = clock.instant.plus(Duration.ofHours(24)).minusMillis(1);
        assertThat(service.search("actual branch")).isSameAs(first);
        clock.instant = clock.instant.plusMillis(1);
        nanos.addAndGet(Duration.ofHours(24).toNanos());
        assertThat(service.search("actual branch")).isEqualTo(first).isNotSameAs(first);
        upstream.verify();
    }

    @Test void differentQueriesShareOneOutboundRequestPerSecondIncludingBoundary() {
        upstream.expect(times(2), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        service.search("first branch");
        nanos.set(Duration.ofSeconds(1).toNanos() - 1);
        assertThatThrownBy(() -> service.search("second branch")).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        nanos.incrementAndGet();
        assertThat(service.search("second branch")).hasSize(1);
        upstream.verify();
    }

    @Test void separateQueriesRetainTheirOwnResultCoordinates() {
        upstream.expect(anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        upstream.expect(anything()).andRespond(withSuccess(PLACE.replace("79.0875,21.125", "72.8777,19.076"), MediaType.APPLICATION_JSON));
        var nagpur = service.search("Nagpur branch");
        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        var mumbai = service.search("Mumbai branch");
        assertThat(nagpur.get(0).latitude()).isEqualTo(21.125);
        assertThat(mumbai.get(0).latitude()).isEqualTo(19.076);
        assertThat(mumbai.get(0).longitude()).isEqualTo(72.8777);
        assertThat(service.search("Nagpur branch")).isSameAs(nagpur);
        assertThat(service.search("Mumbai branch")).isSameAs(mumbai);
        upstream.verify();
    }

    @Test void cacheIsBoundedAndEvictsLeastRecentlyUsedQuery() {
        upstream.expect(times(258), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        for (int index = 0; index < 256; index++) {
            nanos.addAndGet(Duration.ofSeconds(1).toNanos());
            service.search("branch " + index);
        }
        var kept = service.search("branch 0");
        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        service.search("branch 256");
        assertThat(service.search("branch 0")).isSameAs(kept);
        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        service.search("branch 1");
        upstream.verify();
    }

    @Test void emptySearchesAreCachedAndNoCoordinatesAreInvented() {
        upstream.expect(anything()).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.search("unknown branch")).isEmpty();
        assertThat(service.search("unknown branch")).isEmpty();
        upstream.verify();
    }

    @Test void skipsInvalidPointsAndKeepsValidZeroCoordinatesWithoutAName() {
        String response = """
                {"features":[
                  {"geometry":{"type":"LineString","coordinates":[79,21]},"properties":{"name":"Wrong geometry"}},
                  {"geometry":{"type":"Point","coordinates":[181,21]},"properties":{"name":"Bad longitude"}},
                  {"geometry":{"type":"Point","coordinates":[79,-91]},"properties":{"name":"Bad latitude"}},
                  {"geometry":{"type":"Point","coordinates":["79",21]},"properties":{"name":"String coordinates"}},
                  {"geometry":{"type":"Point","coordinates":[79]},"properties":{"name":"Missing latitude"}},
                  {"geometry":{"type":"Point","coordinates":[1e999,21]},"properties":{"name":"Infinite longitude"}},
                  {"geometry":{"type":"Point","coordinates":[0,0]},"properties":{}},
                  {"geometry":{"type":"Point","coordinates":[0,0]},"properties":{"street":"Zero Street","city":"Zero City","state":"Zero City"}}
                ]}
                """;
        upstream.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(service.search("test points")).containsExactly(
                new WorkplaceLocationSearchResult(null, "Zero Street, Zero City", 0, 0));
        upstream.verify();
    }

    @Test void limitsResponseCountAndTextLengthsAndDoesNotLeakProviderIds() {
        String feature = "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[79,21]},\"properties\":{\"name\":\""
                + "x".repeat(1200) + "\",\"osm_id\":123,\"place_id\":456}}";
        upstream.expect(anything()).andRespond(withSuccess("{\"features\":[" + String.join(",", java.util.Collections.nCopies(7, feature)) + "]}", MediaType.APPLICATION_JSON));
        var results = service.search("large response");
        assertThat(results).hasSize(5).allSatisfy(result -> {
            assertThat(result.name()).hasSize(200);
            assertThat(result.address()).hasSize(1000);
        });
        upstream.verify();
    }

    @Test void providerErrorsAreNotRetriedAndStillConsumeRateLimit() {
        upstream.expect(anything()).andRespond(withServerError());
        assertUnavailable(() -> service.search("failed branch"));
        assertThatThrownBy(() -> service.search("another branch")).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        upstream.verify();
    }

    @Test void upstreamThrottlingHasExplicitRateLimitError() {
        upstream.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> service.search("busy branch")).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                error -> assertThat(error.code()).isEqualTo("WORKPLACE_SEARCH_RATE_LIMITED"));
        upstream.verify();
    }

    @Test void timeoutsAreReportedAsUnavailable() {
        upstream.expect(anything()).andRespond(request -> { throw new SocketTimeoutException("stub timeout"); });
        assertUnavailable(() -> service.search("slow branch"));
        upstream.verify();
    }

    @Test void malformedProviderPayloadIsUnavailableRatherThanSuccessfulEmptySearch() {
        upstream.expect(anything()).andRespond(withSuccess("{\"features\":null}", MediaType.APPLICATION_JSON));
        assertUnavailable(() -> service.search("broken response"));
        upstream.verify();
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                error -> { assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE); assertThat(error.code()).isEqualTo("WORKPLACE_SEARCH_UNAVAILABLE"); });
    }

    private static class MutableClock extends Clock {
        Instant instant = Instant.parse("2026-10-27T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
