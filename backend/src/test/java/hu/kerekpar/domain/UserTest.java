package hu.kerekpar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.model.User;
import hu.kerekpar.domain.model.UserRole;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void roleHelpersAreExclusive() {
        User manager = new User("u1", "Kovács Anna", UserRole.MANAGER);
        User renter = new User("u2", "Nagy Péter", UserRole.RENTER);

        assertThat(manager.isManager()).isTrue();
        assertThat(manager.isRenter()).isFalse();
        assertThat(renter.isRenter()).isTrue();
        assertThat(renter.isManager()).isFalse();
    }

    @Test
    void requiredFieldsCannotBeNull() {
        assertThatThrownBy(() -> new User(null, "n", UserRole.RENTER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new User("u", null, UserRole.RENTER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new User("u", "n", null)).isInstanceOf(NullPointerException.class);
    }
}
