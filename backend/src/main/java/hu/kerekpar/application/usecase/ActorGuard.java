package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import hu.kerekpar.domain.model.User;

/** A kérést indító felhasználó feloldása és jogosultság-ellenőrzés (nem hitelesítés). */
class ActorGuard {

    private final UserRepository users;

    ActorGuard(UserRepository users) {
        this.users = users;
    }

    User resolve(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new UnauthenticatedException("ACTOR_REQUIRED", "A művelethez bejelentkezés szükséges.");
        }
        return users.findById(actorId)
                .orElseThrow(() -> new UnauthenticatedException("UNKNOWN_ACTOR",
                        "A bejelentkezett felhasználó már nem létezik."));
    }

    User resolveManager(String actorId, String deniedMessage) {
        User actor = resolve(actorId);
        if (!actor.isManager()) {
            throw new ForbiddenException("MANAGER_ONLY", deniedMessage);
        }
        return actor;
    }

    User resolveRenter(String actorId, String deniedMessage) {
        User actor = resolve(actorId);
        if (!actor.isRenter()) {
            throw new ForbiddenException("RENTER_ONLY", deniedMessage);
        }
        return actor;
    }
}
