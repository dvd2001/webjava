package hu.kerekpar.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.kerekpar.domain.exception.UnauthenticatedException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentActorTest {

    private final CurrentActor actor = new CurrentActor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void actorIsTheSubjectOfTheAuthenticatedToken() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u2")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_RENTER"))));

        assertThat(actor.id()).isEqualTo("u2");
    }

    @Test
    void noAuthenticationMeansUnauthenticated() {
        assertThatThrownBy(actor::id).isInstanceOfSatisfying(UnauthenticatedException.class,
                e -> assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    void anonymousAuthenticationIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(actor::id).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void unauthenticatedTokenIsNotAnActor() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u1", "x"));

        assertThatThrownBy(actor::id).isInstanceOf(UnauthenticatedException.class);
    }
}
