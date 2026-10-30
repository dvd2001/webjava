package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.exception.ForbiddenException;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.exception.NotFoundException;
import hu.kerekpar.domain.model.User;

/** A kérést indító felhasználó feloldása és jogosultság-ellenőrzés (nem hitelesítés). */
class ActorGuard {

    private final UserRepository users;

    ActorGuard(UserRepository users) {
        this.users = users;
    }

    User resolve(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("ACTOR_REQUIRED", "A felhasználó azonosítója kötelező.");
        }
        return users.findById(actorId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "A felhasználó nem található."));
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
