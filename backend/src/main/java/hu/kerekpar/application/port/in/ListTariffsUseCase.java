package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.BikeType;
import hu.kerekpar.domain.model.Tariff;
import java.util.Map;

public interface ListTariffsUseCase {

    Map<BikeType, Tariff> execute();
}
