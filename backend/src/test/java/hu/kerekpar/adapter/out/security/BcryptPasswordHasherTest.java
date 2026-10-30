package hu.kerekpar.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(4);

    @Test
    void hashIsNotThePlainPasswordAndMatchesOnlyTheRightOne() {
        String hash = hasher.hash("Titok-1234");

        assertThat(hash).isNotEqualTo("Titok-1234").startsWith("$2");
        assertThat(hasher.matches("Titok-1234", hash)).isTrue();
        assertThat(hasher.matches("titok-1234", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
    }

    @Test
    void sameInputGivesDifferentSaltedHashes() {
        assertThat(hasher.hash("azonos")).isNotEqualTo(hasher.hash("azonos"));
    }

    @Test
    void fakeHashIsAValidBcryptHashThatNeverMatchesARealisticPassword() {
        assertThat(hasher.fakeHash()).startsWith("$2");
        assertThat(hasher.fakeHash()).isEqualTo(hasher.fakeHash());
        assertThat(hasher.matches("Demo-Kerekpar-2026", hasher.fakeHash())).isFalse();
        assertThat(hasher.matches("", hasher.fakeHash())).isFalse();
    }

    @Test
    void differentInstancesHaveDifferentFakeHashes() {
        assertThat(new BcryptPasswordHasher(4).fakeHash()).isNotEqualTo(new BcryptPasswordHasher(4).fakeHash());
    }
}
