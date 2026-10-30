package hu.kerekpar.application.usecase;

import hu.kerekpar.application.port.in.ListTariffsUseCase;
import hu.kerekpar.application.port.out.TariffRepository;
import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.util.Map;

public class ListTariffsService implements ListTariffsUseCase {

    private final TariffRepository tariffs;

    public ListTariffsService(TariffRepository tariffs) {
        this.tariffs = tariffs;
    }

    @Override
    public Map<BikeType, Tariff> execute() {
        return tariffs.findAll();
    }
}
