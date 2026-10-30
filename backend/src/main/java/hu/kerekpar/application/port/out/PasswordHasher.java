package hu.kerekpar.application.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /**
     * Érvényes, de soha nem egyező hash; ismeretlen felhasználónál is ugyanannyi munkát végzünk,
     * így az időzítés nem árulja el, hogy létezik-e a felhasználónév.
     */
    String fakeHash();
}
