package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.ApiDtos.TariffResponse;
import hu.kerekpar.adapter.in.web.ApiDtos.UserResponse;
import hu.kerekpar.application.port.in.ListTariffsUseCase;
import hu.kerekpar.application.port.in.ListUsersUseCase;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final ListUsersUseCase listUsers;
    private final ListTariffsUseCase listTariffs;

    public ReferenceDataController(ListUsersUseCase listUsers, ListTariffsUseCase listTariffs) {
        this.listUsers = listUsers;
        this.listTariffs = listTariffs;
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return listUsers.execute().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/tariffs")
    public Map<String, TariffResponse> tariffs() {
        return TariffResponse.from(listTariffs.execute());
    }
}
