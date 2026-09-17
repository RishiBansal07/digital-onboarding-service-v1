package com.randombank.onboarding.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseOperationRateLimiterTest {

    @Test
    void spacesOperationsAtConfiguredRate() {
        FakeTime time = new FakeTime();
        DatabaseOperationRateLimiter limiter = new DatabaseOperationRateLimiter(2, time::now, time::sleep);

        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        assertEquals(List.of(500_000_000L, 500_000_000L), time.sleeps);
        assertEquals(1_000_000_000L, time.now());
    }

    @Test
    void doesNotWaitAfterALongIdlePeriod() {
        FakeTime time = new FakeTime();
        DatabaseOperationRateLimiter limiter = new DatabaseOperationRateLimiter(2, time::now, time::sleep);

        limiter.acquire();
        time.advance(2_000_000_000L);
        limiter.acquire();

        assertEquals(List.of(), time.sleeps);
    }

    @Test
    void rejectsNonPositiveRates() {
        FakeTime time = new FakeTime();

        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseOperationRateLimiter(0, time::now, time::sleep));
    }

    @Test
    void serializesConcurrentCallersIntoDistinctSlots() throws InterruptedException {
        FakeTime time = new FakeTime();
        DatabaseOperationRateLimiter limiter = new DatabaseOperationRateLimiter(2, time::now, time::sleep);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> operationTimes = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    limiter.acquire();
                    operationTimes.add(time.now());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        Collections.sort(operationTimes);
        assertEquals(List.of(0L, 500_000_000L, 1_000_000_000L, 1_500_000_000L), operationTimes);
    }

    private static final class FakeTime {
        private final AtomicLong nanos = new AtomicLong();
        private final List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());

        long now() {
            return nanos.get();
        }

        void sleep(long durationNanos) {
            sleeps.add(durationNanos);
            nanos.addAndGet(durationNanos);
        }

        void advance(long durationNanos) {
            nanos.addAndGet(durationNanos);
        }
    }
}

