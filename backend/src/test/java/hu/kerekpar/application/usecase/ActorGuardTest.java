package hu.kerekpar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.adapter.out.memory.InMemoryUserRepository;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import hu.kerekpar.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** A művelet végzőjének feloldása és a szerepkör-ellenőrzés (a használati esetek közös védelme). */
class ActorGuardTest {

    private final ActorGuard guard = new ActorGuard(new InMemoryUserRepository());

    @Test
    void knownActorIsResolved() {
        User user = guard.resolve("u2");

        assertThat(user.name()).isEqualTo("Nagy Péter");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingActorMeansUnauthenticated(String actorId) {
        assertThatThrownBy(() -> guard.resolve(actorId))
                .isInstanceOfSatisfying(UnauthenticatedException.class,
                        e -> assertThat(e.code()).isEqualTo("ACTOR_REQUIRED"));
    }

    @Test
    void actorThatNoLongerExistsMeansUnauthenticatedNotNotFound() {
        assertThatThrownBy(() -> guard.resolve("u999"))
                .isInstanceOfSatisfying(UnauthenticatedException.class,
                        e -> assertThat(e.code()).isEqualTo("UNKNOWN_ACTOR"));
    }

    @Test
    void managerGuardAcceptsManagersAndRejectsRenters() {
        assertThat(guard.resolveManager("u1", "nem vezető").id()).isEqualTo("u1");
        assertThatThrownBy(() -> guard.resolveManager("u2", "csak vezető"))
                .isInstanceOfSatisfying(ForbiddenException.class, e -> {
                    assertThat(e.code()).isEqualTo("MANAGER_ONLY");
                    assertThat(e.getMessage()).isEqualTo("csak vezető");
                });
    }

    @Test
    void renterGuardAcceptsRentersAndRejectsManagers() {
        assertThat(guard.resolveRenter("u3", "nem bérlő").id()).isEqualTo("u3");
        assertThatThrownBy(() -> guard.resolveRenter("u1", "csak bérlő"))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.code()).isEqualTo("RENTER_ONLY"));
    }

    @Test
    void roleGuardsAlsoRequireAKnownActor() {
        assertThatThrownBy(() -> guard.resolveManager("u999", "x")).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> guard.resolveRenter(null, "x")).isInstanceOf(UnauthenticatedException.class);
    }
}
