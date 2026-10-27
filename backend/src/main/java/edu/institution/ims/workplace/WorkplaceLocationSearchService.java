package edu.institution.ims.workplace;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.LongSupplier;

@Service
public class WorkplaceLocationSearchService {
    private static final int MAX_RESULTS = 5;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final long MIN_REQUEST_NANOS = Duration.ofSeconds(1).toNanos();
    private final RestClient client;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Map<String, CachedResults> cache = new LinkedHashMap<>(16, 0.75f, true);
    private boolean requested;
    private long lastRequestNanos;

    @Autowired
    public WorkplaceLocationSearchService(
            @Value("${app.workplace-search.base-url:https://photon.komoot.io}") String baseUrl,
            @Value("${app.workplace-search.connect-timeout-seconds:3}") long connectTimeout,
            @Value("${app.workplace-search.read-timeout-seconds:5}") long readTimeout) {
        this(createClient(baseUrl, connectTimeout, readTimeout), Clock.systemUTC(), System::nanoTime);
    }

    WorkplaceLocationSearchService(RestClient client, Clock clock, LongSupplier nanoTime) {
        this.client = client;
        this.clock = clock;
        this.nanoTime = nanoTime;
    }

    private static RestClient createClient(String baseUrl, long connectTimeout, long readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "InternshipManagementSystem/1.0 (workplace-picker; https://github.com/Mohit2656/internship_m)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    /** Serializes provider calls and bounds this instance to one outbound search per second. */
    public synchronized List<WorkplaceLocationSearchResult> search(String query) {
        String normalized = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (normalized.length() < 3 || normalized.length() > 200) {
            throw new WorkplaceLocationSearchException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE_SEARCH",
                    "Enter between 3 and 200 characters to search for a workplace.");
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        CachedResults cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) return cached.results();
        cache.remove(key);

        long start = nanoTime.getAsLong();
        if (requested && start - lastRequestNanos < MIN_REQUEST_NANOS) throw rateLimited();
        requested = true;
        lastRequestNanos = start;

        List<WorkplaceLocationSearchResult> results;
        try {
            JsonNode response = client.get().uri(builder -> builder.path("/api/")
                    .queryParam("q", "{query}").queryParam("limit", MAX_RESULTS).queryParam("lang", "en")
                    .build(normalized)).retrieve().body(JsonNode.class);
            if (response == null || !response.path("features").isArray()) throw unavailable();
            results = parseResults(response.path("features"));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 429) throw rateLimited();
            throw unavailable();
        } catch (RestClientException error) {
            throw unavailable();
        }
        List<WorkplaceLocationSearchResult> immutable = List.copyOf(results);
        cache.put(key, new CachedResults(immutable, clock.instant().plus(CACHE_TTL)));
        if (cache.size() > MAX_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
        return immutable;
    }

    private static List<WorkplaceLocationSearchResult> parseResults(JsonNode features) {
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
            results.add(new WorkplaceLocationSearchResult(name.isEmpty() ? null : name, address, latitude, longitude));
            if (results.size() == MAX_RESULTS) break;
        }
        return results;
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
