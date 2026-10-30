package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.ListUsersUseCase;
import hu.kerekpar.application.port.out.UserRepository;
import hu.kerekpar.domain.model.User;
import java.util.List;

public class ListUsersService implements ListUsersUseCase {

    private final UserRepository users;

    public ListUsersService(UserRepository users) {
        this.users = users;
    }

    @Override
    public List<User> execute() {
        return users.findAll();
    }
}
