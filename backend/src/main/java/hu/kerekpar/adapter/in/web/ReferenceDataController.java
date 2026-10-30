package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.ReferenceApi;
import hu.kerekpar.adapter.in.web.api.model.TariffDto;
import hu.kerekpar.adapter.in.web.api.model.UserDto;
import hu.kerekpar.application.port.in.ListTariffsUseCase;
import hu.kerekpar.application.port.in.ListUsersUseCase;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReferenceDataController implements ReferenceApi {

    private final ListUsersUseCase listUsers;
    private final ListTariffsUseCase listTariffs;

    public ReferenceDataController(ListUsersUseCase listUsers, ListTariffsUseCase listTariffs) {
        this.listUsers = listUsers;
        this.listTariffs = listTariffs;
    }

    @Override
    public ResponseEntity<List<UserDto>> listUsers() {
        return ResponseEntity.ok(listUsers.execute().stream().map(ApiMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<Map<String, TariffDto>> listTariffs() {
        return ResponseEntity.ok(ApiMapper.toDto(listTariffs.execute()));
    }
}
