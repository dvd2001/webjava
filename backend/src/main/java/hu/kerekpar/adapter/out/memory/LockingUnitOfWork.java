package hu.kerekpar.adapter.out.memory;

import hu.kerekpar.application.port.out.UnitOfWork;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Egyetlen zárral szerializálja az írási használati eseteket (in-memory tárolóhoz elegendő). */
public class LockingUnitOfWork implements UnitOfWork {

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public <T> T execute(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
