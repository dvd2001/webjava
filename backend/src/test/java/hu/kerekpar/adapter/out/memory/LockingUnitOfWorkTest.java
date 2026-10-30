package hu.kerekpar.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LockingUnitOfWorkTest {

    @Test
    void returnsTheResultOfTheWork() {
        assertThat(new LockingUnitOfWork().execute(() -> "kész")).isEqualTo("kész");
    }

    @Test
    void releasesTheLockWhenTheWorkFails() {
        LockingUnitOfWork unitOfWork = new LockingUnitOfWork();

        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            throw new IllegalStateException("hiba");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(unitOfWork.execute(() -> "újra elérhető")).isEqualTo("újra elérhető");
    }

    @Test
    void isReentrantForNestedUse() {
        LockingUnitOfWork unitOfWork = new LockingUnitOfWork();

        assertThat(unitOfWork.execute(() -> unitOfWork.execute(() -> 42))).isEqualTo(42);
    }

    @Test
    void worksNeverOverlapAcrossThreads() throws Exception {
        LockingUnitOfWork unitOfWork = new LockingUnitOfWork();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int id = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    unitOfWork.execute(() -> {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        order.add(id);
                        sleep();
                        inside.decrementAndGet();
                        return null;
                    });
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(maxInside.get()).isEqualTo(1);
        assertThat(order).hasSize(8);
    }

    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
