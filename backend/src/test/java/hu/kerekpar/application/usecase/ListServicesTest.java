package hu.kerekpar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import hu.kerekpar.adapter.out.memory.InMemoryBikeRepository;
import hu.kerekpar.adapter.out.memory.InMemoryTariffRepository;
import hu.kerekpar.adapter.out.memory.InMemoryUserRepository;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import org.junit.jupiter.api.Test;

/** A kereső (lista) használati esetek egyszerű átadása a tárolóknak. */
class ListServicesTest {

    @Test
    void listUsersReturnsAllUsers() {
        assertThat(new ListUsersService(new InMemoryUserRepository()).execute()).hasSize(4);
    }

    @Test
    void listTariffsReturnsEveryType() {
        assertThat(new ListTariffsService(new InMemoryTariffRepository()).execute())
                .containsEntry(BikeType.CITY, new Tariff(300, 15))
                .hasSize(3);
    }

    @Test
    void listBikesReturnsTheStoredBikes() {
        InMemoryBikeRepository bikes = new InMemoryBikeRepository();
        assertThat(new ListBikesService(bikes).execute()).isEmpty();

        bikes.save(new Bike("b1", "City-01", BikeType.CITY, BikeStatus.AVAILABLE));

        assertThat(new ListBikesService(bikes).execute()).extracting(Bike::id).containsExactly("b1");
    }
}
