package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.User;
import java.util.List;

public interface ListUsersUseCase {

    List<User> execute();
}
