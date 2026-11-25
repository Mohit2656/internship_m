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
import java.util.concurrent.*;
import java.util.stream.IntStream;
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
        upstream.expect(requestTo("https://photon.example/api/?q=R%26D%20%2B%20Labs&limit=5&lang=en&countrycode=IN"))
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

    @Test void differentQueriesAreServedThroughTheProviderCoordinator() {
        upstream.expect(times(2), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        service.search("first branch");
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

    @Test void concurrentMissesCannotGrowCachePastConfiguredCapacity() throws Exception {
        service.shutdown();
        var builder = RestClient.builder().baseUrl("https://photon.example");
        upstream = MockRestServiceServer.bindTo(builder).build();
        service = new WorkplaceLocationSearchService(builder.build(), clock, nanos::get,
                4, 32, Duration.ofSeconds(2), Duration.ZERO, 3, Duration.ofSeconds(30),
                new WorkplaceSearchMetrics(null));
        upstream.expect(times(20), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        var futures = IntStream.range(0, 20).mapToObj(index -> service.searchAsync("concurrent branch " + index, null, null)).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
        assertThat(service.cachedEntryCount()).isEqualTo(4);
        upstream.verify();
    }

    @Test void emptySearchesAreCachedAndNoCoordinatesAreInvented() {
        upstream.expect(anything()).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.search("unknown branch")).isEmpty();
        assertThat(service.search("unknown branch")).isEmpty();
        upstream.verify();
    }

    @Test void oneHundredIdenticalConcurrentSearchesUseOneUpstreamCall() throws Exception {
        long benchmarkStart = System.nanoTime();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        upstream.expect(times(1), anything()).andRespond(request -> {
            entered.countDown();
            try { assertThat(release.await(2, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            return withSuccess(PLACE, MediaType.APPLICATION_JSON).createResponse(request);
        });
        var futures = IntStream.range(0, 100)
                .mapToObj(index -> service.searchAsync("Concurrent Branch", 21.1498, 79.082))
                .toList();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
        assertThat(futures).allSatisfy(future -> assertThat(future.join()).hasSize(1));
        System.out.printf("PHASE3 identical=100 success=100 upstream=1 elapsedMs=%.2f%n",
                (System.nanoTime() - benchmarkStart) / 1_000_000.0);
        upstream.verify();
    }

    @Test void cachedResultServesTenThousandRequestsWithoutAnotherUpstreamCall() {
        upstream.expect(times(1), anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        service.search("cached branch");
        long start = System.nanoTime();
        for (int index = 0; index < 10_000; index++) assertThat(service.search(" CACHED  branch ")).hasSize(1);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf("PHASE3 cacheHits=10000 upstream=1 elapsedMs=%.2f throughputPerSecond=%.0f%n",
                elapsedMs, 10_000_000.0 / elapsedMs);
        upstream.verify();
    }

    @Test void failedInFlightEntryIsRemovedAndTheSameQueryCanRecover() {
        upstream.expect(anything()).andRespond(withServerError());
        upstream.expect(anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        assertUnavailable(() -> service.search("recovering branch"));
        assertThat(service.search("recovering branch")).hasSize(1);
        upstream.verify();
    }

    @Test void circuitSuppressesCallsAfterThreeFailuresAndRecoversAfterOpenDuration() {
        upstream.expect(times(3), anything()).andRespond(withServerError());
        upstream.expect(anything()).andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        for (int attempt = 0; attempt < 3; attempt++) {
            int current = attempt;
            assertUnavailable(() -> service.search("failed branch " + current));
        }
        assertUnavailable(() -> service.search("suppressed branch"));
        clock.instant = clock.instant.plusSeconds(30);
        assertThat(service.search("recovered branch")).hasSize(1);
        upstream.verify();
    }

    @Test void emptySearchCacheExpiresAfterFiveMinutesWithoutChangingPositiveTtl() {
        upstream.expect(times(2), anything()).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        assertThat(service.search("unknown branch")).isEmpty();
        clock.instant = clock.instant.plus(Duration.ofMinutes(5)).minusMillis(1);
        assertThat(service.search("unknown branch")).isEmpty();
        clock.instant = clock.instant.plusMillis(1);
        nanos.addAndGet(Duration.ofMinutes(5).toNanos());
        assertThat(service.search("unknown branch")).isEmpty();
        upstream.verify();
    }

    @Test void sendsIndiaFilterAndOptionalPhotonLocationBiasAndSeparatesCacheKeys() {
        upstream.expect(requestTo("https://photon.example/api/?q=Nagpur%20office&limit=5&lang=en&countrycode=IN&lat=21.1498&lon=79.082&zoom=12&location_bias_scale=0.1"))
                .andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        upstream.expect(requestTo("https://photon.example/api/?q=Nagpur%20office&limit=5&lang=en&countrycode=IN"))
                .andRespond(withSuccess(PLACE, MediaType.APPLICATION_JSON));
        assertThat(service.search("Nagpur office", 21.1498, 79.082)).hasSize(1);
        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(service.search("Nagpur office")).hasSize(1);
        upstream.verify();
    }

    @Test void rejectsPartialOrInvalidBiasBeforeCallingPhoton() {
        for (Double[] bias : new Double[][]{{21.0, null}, {null, 79.0}, {91.0, 79.0}, {21.0, 181.0}, {Double.NaN, 79.0}}) {
            assertThatThrownBy(() -> service.search("Nagpur office", bias[0], bias[1]))
                    .isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                            error -> assertThat(error.code()).isEqualTo("INVALID_WORKPLACE_SEARCH_BIAS"));
        }
        upstream.verify();
    }

    @Test void conservativelyClassifiesPhotonMetadataAndFiltersUnrelatedBusinesses() {
        String response = """
                {"features":[
                  {"geometry":{"type":"Point","coordinates":[79.08,21.14]},"properties":{"name":"Virtual Galaxy Infotech","osm_key":"office","osm_value":"company","street":"Vivekanand Nagar","city":"Nagpur","country":"India"}},
                  {"geometry":{"type":"Point","coordinates":[79.09,21.13]},"properties":{"name":"Itech Galaxy","osm_key":"shop","osm_value":"supermarket","street":"Other Road","city":"Nagpur","country":"India"}},
                  {"geometry":{"type":"Point","coordinates":[79.10,21.12]},"properties":{"name":"Plot 26","osm_key":"building","osm_value":"yes","housenumber":"26","street":"Vivekanand Nagar","city":"Nagpur","country":"India"}},
                  {"geometry":{"type":"Point","coordinates":[79.11,21.11]},"properties":{"name":"Ring Road","osm_key":"highway","osm_value":"secondary","postcode":"440015","city":"Nagpur","country":"India"}},
                  {"geometry":{"type":"Point","coordinates":[79.082,21.149]},"properties":{"name":"Nagpur","osm_key":"place","osm_value":"city","state":"Maharashtra","country":"India"}}
                ]}
                """;
        upstream.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(service.search("Virtual Galaxy Infotech Nagpur 440015"))
                .extracting(WorkplaceLocationSearchResult::name, WorkplaceLocationSearchResult::matchQuality)
                .containsExactly(
                        tuple("Virtual Galaxy Infotech", WorkplaceLocationMatchQuality.EXACT_BUSINESS),
                        tuple("Plot 26", WorkplaceLocationMatchQuality.APPROXIMATE_ADDRESS),
                        tuple("Ring Road", WorkplaceLocationMatchQuality.STREET_OR_POSTCODE),
                        tuple("Nagpur", WorkplaceLocationMatchQuality.LOCALITY_OR_CITY));
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
                + "x".repeat(1200) + "\",\"street\":\"Long Road\",\"osm_id\":123,\"place_id\":456}}";
        upstream.expect(anything()).andRespond(withSuccess("{\"features\":[" + String.join(",", java.util.Collections.nCopies(7, feature)) + "]}", MediaType.APPLICATION_JSON));
        var results = service.search("large response");
        assertThat(results).hasSize(5).allSatisfy(result -> {
            assertThat(result.name()).hasSize(200);
            assertThat(result.address()).hasSize(1000);
        });
        upstream.verify();
    }

    @Test void providerErrorsAreNotRetriedOrCached() {
        upstream.expect(times(2), anything()).andRespond(withServerError());
        assertUnavailable(() -> service.search("failed branch"));
        assertUnavailable(() -> service.search("another branch"));
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
