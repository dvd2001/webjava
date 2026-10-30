package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.application.port.out.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** Az írási use case-ek egyetlen adatbázis-tranzakcióban futnak (kivétel esetén visszagörgetés). */
class PostgresUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    PostgresUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
