package edu.institution.ims.workplace;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkplaceSearchMetrics {
    private final Counter requests, hits, misses, coalesced, rejected, upstream429, failures;
    private final Timer latency;
    private final AtomicInteger queueDepth = new AtomicInteger();

    WorkplaceSearchMetrics(MeterRegistry registry) {
        requests = counter(registry, "ims.workplace.search.requests");
        hits = counter(registry, "ims.workplace.search.cache.hits");
        misses = counter(registry, "ims.workplace.search.cache.misses");
        coalesced = counter(registry, "ims.workplace.search.coalesced");
        rejected = counter(registry, "ims.workplace.search.queue.rejections");
        upstream429 = counter(registry, "ims.workplace.search.upstream.rate_limited");
        failures = counter(registry, "ims.workplace.search.upstream.failures");
        latency = registry == null ? null : Timer.builder("ims.workplace.search.upstream.latency").register(registry);
        if (registry != null) registry.gauge("ims.workplace.search.queue.depth", queueDepth);
    }

    private static Counter counter(MeterRegistry registry, String name) {
        return registry == null ? null : Counter.builder(name).register(registry);
    }
    private static void increment(Counter counter) { if (counter != null) counter.increment(); }
    void request() { increment(requests); }
    void cacheHit() { increment(hits); }
    void cacheMiss() { increment(misses); }
    void coalesced() { increment(coalesced); }
    void queueRejected() { increment(rejected); }
    void upstream429() { increment(upstream429); }
    void upstreamFailure() { increment(failures); }
    void queueDepth(int value) { queueDepth.set(Math.max(0, value)); }
    void latency(Duration duration) { if (latency != null) latency.record(duration); }
}
