package edu.institution.ims.workplace;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

@Service
public class WorkplaceLocationSearchService {
    private static final int MAX_RESULTS = 5;
    private static final Duration POSITIVE_CACHE_TTL = Duration.ofHours(24);
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Set<String> BUSINESS_KEYS = Set.of("amenity", "craft", "healthcare", "leisure", "office", "shop", "tourism");
    private static final Set<String> LOCALITY_VALUES = Set.of("city", "town", "village", "hamlet", "suburb", "neighbourhood", "quarter", "locality", "district", "county", "state", "region");
    private static final Set<String> QUERY_STOP_WORDS = Set.of("india", "ind", "maharashtra", "limited", "ltd", "private", "pvt", "llp", "company", "co", "branch", "office");
    private final RestClient client;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final WorkplaceSearchProviderCoordinator coordinator;
    private final WorkplaceSearchMetrics metrics;
    private final int maxCacheEntries;
    private final Map<String, CachedResults> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Object cacheLock = new Object();
    private final ConcurrentMap<String, CompletableFuture<List<WorkplaceLocationSearchResult>>> inFlight = new ConcurrentHashMap<>();
    private final Object circuitLock = new Object();
    private final int circuitFailureThreshold;
    private final Duration circuitOpenDuration;
    private int consecutiveProviderFailures;
    private Instant circuitOpenUntil = Instant.EPOCH;

    @Autowired
    public WorkplaceLocationSearchService(
            @Value("${app.workplace-search.base-url:https://photon.komoot.io}") String baseUrl,
            @Value("${app.workplace-search.connect-timeout-seconds:3}") long connectTimeout,
            @Value("${app.workplace-search.read-timeout-seconds:5}") long readTimeout,
            @Value("${app.workplace-search.cache-capacity:256}") int cacheCapacity,
            @Value("${app.workplace-search.queue-capacity:8}") int queueCapacity,
            @Value("${app.workplace-search.queue-wait-seconds:4}") long queueWaitSeconds,
            @Value("${app.workplace-search.upstream-interval-millis:1000}") long upstreamIntervalMillis,
            @Value("${app.workplace-search.circuit-failure-threshold:3}") int circuitFailureThreshold,
            @Value("${app.workplace-search.circuit-open-seconds:30}") long circuitOpenSeconds,
            MeterRegistry meterRegistry) {
        this(createClient(baseUrl, connectTimeout, readTimeout), Clock.systemUTC(), System::nanoTime,
                cacheCapacity, queueCapacity, Duration.ofSeconds(queueWaitSeconds),
                Duration.ofMillis(upstreamIntervalMillis), circuitFailureThreshold,
                Duration.ofSeconds(circuitOpenSeconds), new WorkplaceSearchMetrics(meterRegistry));
    }

    WorkplaceLocationSearchService(RestClient client, Clock clock, LongSupplier nanoTime) {
        this(client, clock, nanoTime, 256, 256, Duration.ofSeconds(5), Duration.ZERO,
                3, Duration.ofSeconds(30), new WorkplaceSearchMetrics(null));
    }

    WorkplaceLocationSearchService(RestClient client, Clock clock, LongSupplier nanoTime,
            int cacheCapacity, int queueCapacity, Duration queueWait, Duration upstreamInterval,
            int circuitFailureThreshold, Duration circuitOpenDuration, WorkplaceSearchMetrics metrics) {
        this.client = client;
        this.clock = clock;
        this.nanoTime = nanoTime;
        if (cacheCapacity < 1 || circuitFailureThreshold < 1 || circuitOpenDuration.isNegative()) {
            throw new IllegalArgumentException("Invalid workplace search resilience settings");
        }
        this.maxCacheEntries = cacheCapacity;
        this.circuitFailureThreshold = circuitFailureThreshold;
        this.circuitOpenDuration = circuitOpenDuration;
        this.metrics = metrics;
        this.coordinator = new WorkplaceSearchProviderCoordinator(queueCapacity, upstreamInterval, queueWait, metrics);
    }

    private static RestClient createClient(String baseUrl, long connectTimeout, long readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "InternshipManagementSystem/1.0 (workplace-picker; https://github.com/Mohit2656/internship_m)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    public List<WorkplaceLocationSearchResult> search(String query) {
        return search(query, null, null);
    }

    public List<WorkplaceLocationSearchResult> search(String query, Double latitude, Double longitude) {
        try {
            return searchAsync(query, latitude, longitude).join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    public CompletableFuture<List<WorkplaceLocationSearchResult>> searchAsync(String query, Double latitude, Double longitude) {
        metrics.request();
        String normalized = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (normalized.length() < 3 || normalized.length() > 200) {
            throw new WorkplaceLocationSearchException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE_SEARCH",
                    "Enter between 3 and 200 characters to search for a workplace.");
        }
        validateBias(latitude, longitude);
        String key = normalized.toLowerCase(Locale.ROOT) + biasKey(latitude, longitude);
        List<WorkplaceLocationSearchResult> cached = cached(key);
        if (cached != null) {
            metrics.cacheHit();
            return CompletableFuture.completedFuture(cached);
        }
        metrics.cacheMiss();

        CompletableFuture<List<WorkplaceLocationSearchResult>> shared = new CompletableFuture<>();
        CompletableFuture<List<WorkplaceLocationSearchResult>> existing = inFlight.putIfAbsent(key, shared);
        if (existing != null) {
            metrics.coalesced();
            return existing.thenApply(results -> results);
        }
        coordinator.submit(() -> requestProvider(normalized, latitude, longitude)).whenComplete((results, error) -> {
            try {
                if (error == null) {
                    putCached(key, results);
                    shared.complete(results);
                } else {
                    shared.completeExceptionally(unwrap(error));
                }
            } finally {
                inFlight.remove(key, shared);
            }
        });
        return shared.thenApply(results -> results);
    }

    private List<WorkplaceLocationSearchResult> requestProvider(String normalized, Double latitude, Double longitude) {
        if (circuitOpen()) throw unavailable();
        long start = nanoTime.getAsLong();
        try {
            JsonNode response = client.get().uri(builder -> {
                builder.path("/api/").queryParam("q", "{query}").queryParam("limit", MAX_RESULTS)
                        .queryParam("lang", "en").queryParam("countrycode", "IN");
                if (latitude != null) builder.queryParam("lat", latitude).queryParam("lon", longitude)
                        .queryParam("zoom", 12).queryParam("location_bias_scale", 0.1);
                return builder.build(normalized);
            }).retrieve().body(JsonNode.class);
            if (response == null || !response.path("features").isArray()) {
                providerFailed();
                metrics.upstreamFailure();
                throw unavailable();
            }
            List<WorkplaceLocationSearchResult> results = List.copyOf(parseResults(response.path("features"), normalized));
            providerSucceeded();
            return results;
        } catch (RestClientResponseException error) {
            providerFailed();
            if (error.getStatusCode().value() == 429) {
                metrics.upstream429();
                throw rateLimited();
            }
            metrics.upstreamFailure();
            throw unavailable();
        } catch (RestClientException error) {
            providerFailed();
            metrics.upstreamFailure();
            throw unavailable();
        } finally {
            long elapsed = Math.max(0, nanoTime.getAsLong() - start);
            metrics.latency(Duration.ofNanos(elapsed));
        }
    }

    private List<WorkplaceLocationSearchResult> cached(String key) {
        synchronized (cacheLock) {
            CachedResults value = cache.get(key);
            if (value != null && clock.instant().isBefore(value.expiresAt())) return value.results();
            cache.remove(key);
            return null;
        }
    }

    private void putCached(String key, List<WorkplaceLocationSearchResult> results) {
        List<WorkplaceLocationSearchResult> immutable = List.copyOf(results);
        Duration ttl = immutable.isEmpty() ? NEGATIVE_CACHE_TTL : POSITIVE_CACHE_TTL;
        synchronized (cacheLock) {
            cache.put(key, new CachedResults(immutable, clock.instant().plus(ttl)));
            while (cache.size() > maxCacheEntries) cache.remove(cache.keySet().iterator().next());
        }
    }

    private boolean circuitOpen() {
        synchronized (circuitLock) {
            if (clock.instant().isBefore(circuitOpenUntil)) return true;
            if (!circuitOpenUntil.equals(Instant.EPOCH)) {
                circuitOpenUntil = Instant.EPOCH;
                consecutiveProviderFailures = 0;
            }
            return false;
        }
    }

    private void providerSucceeded() {
        synchronized (circuitLock) {
            consecutiveProviderFailures = 0;
            circuitOpenUntil = Instant.EPOCH;
        }
    }

    private void providerFailed() {
        synchronized (circuitLock) {
            if (++consecutiveProviderFailures >= circuitFailureThreshold) {
                circuitOpenUntil = clock.instant().plus(circuitOpenDuration);
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    @PreDestroy
    void shutdown() { coordinator.close(); }

    int cachedEntryCount() {
        synchronized (cacheLock) { return cache.size(); }
    }

    private static List<WorkplaceLocationSearchResult> parseResults(JsonNode features, String query) {
        var results = new ArrayList<WorkplaceLocationSearchResult>();
        for (JsonNode feature : features) {
            JsonNode geometry = feature.path("geometry"), coordinates = geometry.path("coordinates");
            if (!"Point".equals(geometry.path("type").asText()) || !coordinates.isArray() || coordinates.size() < 2
                    || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) continue;
            double longitude = coordinates.get(0).doubleValue(), latitude = coordinates.get(1).doubleValue();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) continue;
            JsonNode properties = feature.path("properties");
            String name = text(properties.path("name"), 200);
            var addressParts = new LinkedHashSet<String>();
            for (String field : List.of("name", "housenumber", "street", "postcode", "city", "district", "state", "country")) {
                String part = text(properties.path(field), 1000);
                if (!part.isEmpty()) addressParts.add(part);
            }
            String address = limit(String.join(", ", addressParts), 1000);
            if (address.isEmpty()) continue;
            WorkplaceLocationMatchQuality quality = classify(properties, name, query);
            if (quality == null) continue;
            results.add(new WorkplaceLocationSearchResult(name.isEmpty() ? null : name, address, latitude, longitude, quality));
            if (results.size() == MAX_RESULTS) break;
        }
        return results;
    }

    private static WorkplaceLocationMatchQuality classify(JsonNode properties, String name, String query) {
        String osmKey = normalizedText(properties.path("osm_key"));
        String osmValue = normalizedText(properties.path("osm_value"));
        String type = normalizedText(properties.path("type"));
        boolean locality = "place".equals(osmKey) || "boundary".equals(osmKey)
                || LOCALITY_VALUES.contains(osmValue) || LOCALITY_VALUES.contains(type);
        if (locality) return WorkplaceLocationMatchQuality.LOCALITY_OR_CITY;

        boolean street = "highway".equals(osmKey) || "street".equals(osmValue) || "street".equals(type);
        boolean postcode = !text(properties.path("postcode"), 100).isEmpty()
                && ("postcode".equals(osmValue) || "postcode".equals(type) || name.isEmpty());
        if (street || postcode) return WorkplaceLocationMatchQuality.STREET_OR_POSTCODE;

        boolean providerBusiness = BUSINESS_KEYS.contains(osmKey) || "company".equals(osmValue)
                || "business".equals(type) || "poi".equals(type);
        if (providerBusiness) {
            return businessNameMatches(name, query) ? WorkplaceLocationMatchQuality.EXACT_BUSINESS : null;
        }

        boolean addressEvidence = !text(properties.path("housenumber"), 100).isEmpty()
                || !text(properties.path("street"), 1000).isEmpty() || "building".equals(osmKey)
                || "house".equals(osmValue) || "address".equals(type);
        return addressEvidence ? WorkplaceLocationMatchQuality.APPROXIMATE_ADDRESS : null;
    }

    private static boolean businessNameMatches(String name, String query) {
        Set<String> nameTokens = meaningfulTokens(name);
        Set<String> queryTokens = meaningfulTokens(query);
        if (nameTokens.isEmpty() || queryTokens.isEmpty()) return false;
        long overlap = nameTokens.stream().filter(queryTokens::contains).count();
        if (nameTokens.size() == 1) return overlap == 1;
        return overlap >= 2 && overlap * 2 >= nameTokens.size();
    }

    private static Set<String> meaningfulTokens(String value) {
        var tokens = new LinkedHashSet<String>();
        for (String token : normalizedText(value).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2 && !QUERY_STOP_WORDS.contains(token)) tokens.add(token);
        }
        return tokens;
    }

    private static String normalizedText(JsonNode value) {
        return value.isTextual() ? normalizedText(value.asText()) : "";
    }

    private static String normalizedText(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).strip();
    }

    private static void validateBias(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null) || latitude != null && (!Double.isFinite(latitude)
                || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
            throw new WorkplaceLocationSearchException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE_SEARCH_BIAS",
                    "Provide both valid latitude and longitude values for workplace search bias.");
        }
    }

    private static String biasKey(Double latitude, Double longitude) {
        return latitude == null ? "|IN" : String.format(Locale.ROOT, "|IN|%.5f|%.5f", latitude, longitude);
    }

    private static String text(JsonNode value, int maxLength) {
        return value.isTextual() ? limit(value.textValue().strip().replaceAll("\\s+", " "), maxLength) : "";
    }

    private static String limit(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength).stripTrailing() : value;
    }

    private static WorkplaceLocationSearchException rateLimited() {
        return new WorkplaceLocationSearchException(HttpStatus.TOO_MANY_REQUESTS, "WORKPLACE_SEARCH_RATE_LIMITED",
                "Workplace search is busy. Wait a moment and search again.");
    }

    private static WorkplaceLocationSearchException unavailable() {
        return new WorkplaceLocationSearchException(HttpStatus.SERVICE_UNAVAILABLE, "WORKPLACE_SEARCH_UNAVAILABLE",
                "Workplace search is temporarily unavailable. Retry, or select the workplace on the map.");
    }

    private record CachedResults(List<WorkplaceLocationSearchResult> results, Instant expiresAt) {}
}
