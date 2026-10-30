package hu.kerekpar.application.port.out;

import java.util.function.Supplier;

/** Az összetartozó olvasás-módosítás lépések atomi végrehajtása (később tranzakcióra cserélhető). */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);
}
