package hu.kerekpar.adapter.out.persistence;

import hu.kerekpar.domain.model.User;

final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(EntityIds.toDomain(EntityIds.USER, entity.getId()), entity.getName(), entity.getRole());
    }
}
