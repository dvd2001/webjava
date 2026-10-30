package hu.kerekpar.adapter.out.security;

import hu.kerekpar.application.port.out.PasswordHasher;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final String fakeHash;

    public BcryptPasswordHasher(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
        this.fakeHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    @Override
    public String fakeHash() {
        return fakeHash;
    }
}
