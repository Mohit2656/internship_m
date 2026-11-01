package edu.institution.ims.workplace;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class WorkplaceSearchProviderCoordinatorTest {
    private static WorkplaceSearchMetrics metrics() { return new WorkplaceSearchMetrics(null); }

    @Test void fullQueueRejectsWithoutCreatingUnboundedWorkers() throws Exception {
        try (var coordinator = new WorkplaceSearchProviderCoordinator(1, Duration.ZERO, Duration.ofSeconds(1), metrics())) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var running = coordinator.submit(() -> await(entered, release));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var queued = coordinator.submit(() -> "queued");
            var rejected = coordinator.submit(() -> "rejected");
            assertBusy(rejected);
            assertThat(coordinator.queueDepth()).isEqualTo(1);
            release.countDown();
            assertThat(running.get(1, TimeUnit.SECONDS)).isEqualTo("running");
            assertThat(queued.get(1, TimeUnit.SECONDS)).isEqualTo("queued");
        }
    }

    @Test void queuedOperationTimesOutAndIsRemovedBeforeCallingProvider() throws Exception {
        try (var coordinator = new WorkplaceSearchProviderCoordinator(1, Duration.ZERO, Duration.ofMillis(40), metrics())) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var running = coordinator.submit(() -> await(entered, release));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            AtomicInteger calls = new AtomicInteger();
            var timedOut = coordinator.submit(() -> { calls.incrementAndGet(); return "late"; });
            assertBusy(timedOut);
            assertThat(calls).hasValue(0);
            release.countDown();
            assertThat(running.get(1, TimeUnit.SECONDS)).isEqualTo("running");
        }
    }

    @Test void providerStartsRespectConfiguredInterval() throws Exception {
        try (var coordinator = new WorkplaceSearchProviderCoordinator(3, Duration.ofMillis(40), Duration.ofSeconds(1), metrics())) {
            var starts = new CopyOnWriteArrayList<Long>();
            List<CompletableFuture<Integer>> calls = IntStream.range(0, 3)
                    .mapToObj(index -> coordinator.submit(() -> { starts.add(System.nanoTime()); return index; }))
                    .toList();
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
            assertThat(starts).hasSize(3);
            assertThat(starts.get(1) - starts.get(0)).isGreaterThanOrEqualTo(Duration.ofMillis(35).toNanos());
            assertThat(starts.get(2) - starts.get(1)).isGreaterThanOrEqualTo(Duration.ofMillis(35).toNanos());
        }
    }

    @Test void fiveHundredUniqueMissesRemainBoundedAndMostlyRejectImmediately() throws Exception {
        long start = System.nanoTime();
        var latencies = new CopyOnWriteArrayList<Double>();
        int threadsBefore = Thread.getAllStackTraces().size();
        long heapBefore = usedHeap();
        try (var coordinator = new WorkplaceSearchProviderCoordinator(8, Duration.ofSeconds(1), Duration.ofMillis(100), metrics())) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var first = coordinator.submit(() -> await(entered, release));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            List<CompletableFuture<Integer>> burst = IntStream.range(0, 499)
                    .mapToObj(index -> timed(coordinator.submit(() -> index), start, latencies)).toList();
            long rejected = burst.stream().filter(CompletableFuture::isCompletedExceptionally).count();
            assertThat(coordinator.queueDepth()).isLessThanOrEqualTo(8);
            assertThat(rejected).isGreaterThanOrEqualTo(490);
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("running");
            CompletableFuture.allOf(burst.stream().map(future -> future.handle((value, error) -> null))
                    .toArray(CompletableFuture[]::new)).get(1, TimeUnit.SECONDS);
            long failures = burst.stream().filter(CompletableFuture::isCompletedExceptionally).count();
            System.out.printf("PHASE3 simulatedStudents=500 success=%d rateLimited=%d queueMax=8 elapsedMs=%.2f p50Ms=%.2f p95Ms=%.2f p99Ms=%.2f threadDelta=%d heapDeltaBytes=%d%n",
                    500 - failures, failures, (System.nanoTime() - start) / 1_000_000.0,
                    percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99),
                    Thread.getAllStackTraces().size() - threadsBefore, usedHeap() - heapBefore);
            assertThat(failures).isEqualTo(499);
        }
    }

    @Test void oneHundredUniqueMissesStayWithinScaledQueueAndWaitBounds() throws Exception {
        long start = System.nanoTime();
        var latencies = new CopyOnWriteArrayList<Double>();
        try (var coordinator = new WorkplaceSearchProviderCoordinator(8, Duration.ofMillis(10), Duration.ofMillis(40), metrics())) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var first = coordinator.submit(() -> await(entered, release));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            List<CompletableFuture<Integer>> burst = IntStream.range(0, 99)
                    .mapToObj(index -> timed(coordinator.submit(() -> index), start, latencies)).toList();
            release.countDown();
            CompletableFuture.allOf(burst.stream().map(future -> future.handle((value, error) -> null))
                    .toArray(CompletableFuture[]::new)).get(1, TimeUnit.SECONDS);
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("running");
            long failures = burst.stream().filter(CompletableFuture::isCompletedExceptionally).count();
            System.out.printf("PHASE3 unique=100 success=%d rateLimited=%d scaledIntervalMs=10 scaledWaitMs=40 elapsedMs=%.2f p50Ms=%.2f p95Ms=%.2f p99Ms=%.2f%n",
                    100 - failures, failures, (System.nanoTime() - start) / 1_000_000.0,
                    percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99));
            assertThat(failures).isBetween(90L, 99L);
        }
    }

    private static String await(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
            return "running";
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
        }
    }

    private static void assertBusy(CompletableFuture<?> future) {
        assertThatThrownBy(future::join).isInstanceOfSatisfying(CompletionException.class, wrapper -> {
            assertThat(wrapper.getCause()).isInstanceOfSatisfying(WorkplaceLocationSearchException.class,
                    error -> assertThat(error.code()).isEqualTo("WORKPLACE_SEARCH_RATE_LIMITED"));
        });
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static <T> CompletableFuture<T> timed(CompletableFuture<T> future, long start,
            CopyOnWriteArrayList<Double> latencies) {
        future.whenComplete((value, error) -> latencies.add((System.nanoTime() - start) / 1_000_000.0));
        return future;
    }

    private static double percentile(List<Double> values, int percentile) {
        var sorted = values.stream().sorted().toList();
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1));
    }
}
