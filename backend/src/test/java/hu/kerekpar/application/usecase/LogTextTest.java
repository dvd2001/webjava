package hu.kerekpar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogTextTest {

    @Test
    void nullIsADash() {
        assertThat(LogText.printable(null)).isEqualTo("-");
    }

    @Test
    void controlCharactersAreReplaced() {
        assertThat(LogText.printable("a\nb\rc\td\u001be\u0000")).isEqualTo("a?b?c?d?e?");
    }

    @Test
    void longValuesAreTruncatedWithAnEllipsis() {
        assertThat(LogText.printable("x".repeat(40))).isEqualTo("x".repeat(40));
        assertThat(LogText.printable("x".repeat(41))).isEqualTo("x".repeat(40) + "…");
    }

    @Test
    void ordinaryAccentedTextIsKept() {
        assertThat(LogText.printable("Árpád-01")).isEqualTo("Árpád-01");
    }
}
