package hu.kerekpar.adapter.in.web.security;

import hu.kerekpar.domain.exception.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * A műveletet végző felhasználó azonosítója. Kizárólag a hitelesített principalból (a token {@code sub} claimjéből)
 * származik, a kérés más részéből (fejléc, törzs, URL) soha.
 */
@Component
public class CurrentActor {

    public String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("UNAUTHENTICATED", "A művelethez bejelentkezés szükséges.");
        }
        return authentication.getName();
    }
}
