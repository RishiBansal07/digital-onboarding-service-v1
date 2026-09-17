package com.randombank.onboarding.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Rate limiter for database operations.
 * Spaces database operation starts so the configured throughput is never exceeded.
 * Callers wait instead of being rejected, which allows multi-query transactions to
 * complete without failing halfway through.
 */
@Component
public class DatabaseOperationRateLimiter {

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final ReentrantLock lock = new ReentrantLock(true);
    private long nextOperationNanos;

    @Autowired
    public DatabaseOperationRateLimiter(
            @Value("${app.db.max-operations-per-second:2}") int maxOperationsPerSecond) {
        this(maxOperationsPerSecond, System::nanoTime, LockSupport::parkNanos);
    }

    DatabaseOperationRateLimiter(int maxOperationsPerSecond,
                                 LongSupplier nanoTime,
                                 Sleeper sleeper) {
        if (maxOperationsPerSecond <= 0) {
            throw new IllegalArgumentException("maxOperationsPerSecond must be greater than zero");
        }
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / maxOperationsPerSecond;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /**
     * Blocks until this operation's scheduled start time. The fair lock preserves
     * arrival order while preventing simultaneous callers from reserving the same slot.
     */
    public void acquire() {
        lock.lock();
        try {
            long now = nanoTime.getAsLong();
            long waitNanos = Math.max(0, nextOperationNanos - now);
            if (waitNanos > 0) {
                sleeper.sleep(waitNanos);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Interrupted while waiting for database access");
                }
                now = nanoTime.getAsLong();
            }
            nextOperationNanos = Math.max(now, nextOperationNanos) + intervalNanos;
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long nanos);
    }
}

