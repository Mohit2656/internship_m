package edu.institution.ims.workplace;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

/**
 * Deployment-local, provider-aware queue and start-rate limiter. It deliberately has one bounded
 * worker so queued misses cannot create unbounded threads or exceed the configured Photon cadence.
 */
final class WorkplaceSearchProviderCoordinator implements AutoCloseable {
    private final ThreadPoolExecutor worker;
    private final ScheduledExecutorService timer;
    private final long intervalNanos;
    private final long maxWaitNanos;
    private final AtomicLong nextStartNanos = new AtomicLong();
    private final WorkplaceSearchMetrics metrics;

    WorkplaceSearchProviderCoordinator(int queueCapacity, Duration interval, Duration maxWait,
            WorkplaceSearchMetrics metrics) {
        if (queueCapacity < 1 || interval.isNegative() || maxWait.isNegative() || maxWait.isZero()) {
            throw new IllegalArgumentException("Invalid workplace search coordinator settings");
        }
        this.intervalNanos = interval.toNanos();
        this.maxWaitNanos = maxWait.toNanos();
        this.metrics = metrics;
        ThreadFactory workerFactory = daemonFactory("workplace-search-provider");
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), workerFactory, new ThreadPoolExecutor.AbortPolicy());
        timer = Executors.newSingleThreadScheduledExecutor(daemonFactory("workplace-search-timeout"));
    }

    <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        AtomicBoolean started = new AtomicBoolean();
        long deadline = System.nanoTime() + maxWaitNanos;
        Runnable task = () -> {
            if (result.isDone()) return;
            long wait = nextStartNanos.get() - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            if (Thread.currentThread().isInterrupted()) {
                result.completeExceptionally(unavailable());
                return;
            }
            if (result.isDone() || System.nanoTime() > deadline) {
                result.completeExceptionally(busy());
                return;
            }
            if (!started.compareAndSet(false, true)) return;
            nextStartNanos.set(System.nanoTime() + intervalNanos);
            try {
                result.complete(operation.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        };
        try {
            worker.execute(task);
            metrics.queueDepth(worker.getQueue().size());
        } catch (RejectedExecutionException rejected) {
            metrics.queueRejected();
            result.completeExceptionally(busy());
            return result;
        }
        ScheduledFuture<?> timeout = timer.schedule(() -> {
            if (started.compareAndSet(false, true) && result.completeExceptionally(busy())) {
                worker.remove(task);
                metrics.queueRejected();
            }
            metrics.queueDepth(worker.getQueue().size());
        }, maxWaitNanos, TimeUnit.NANOSECONDS);
        result.whenComplete((ignored, error) -> {
            timeout.cancel(false);
            metrics.queueDepth(worker.getQueue().size());
        });
        return result;
    }

    int queueDepth() { return worker.getQueue().size(); }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static WorkplaceLocationSearchException busy() {
        return new WorkplaceLocationSearchException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "WORKPLACE_SEARCH_RATE_LIMITED",
                "Workplace search is temporarily busy. Wait a moment, or select the workplace manually on the map.");
    }

    private static WorkplaceLocationSearchException unavailable() {
        return new WorkplaceLocationSearchException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "WORKPLACE_SEARCH_UNAVAILABLE",
                "Workplace search is temporarily unavailable. Retry, or select the workplace on the map.");
    }

    @Override public void close() {
        worker.shutdownNow();
        timer.shutdownNow();
    }
}
