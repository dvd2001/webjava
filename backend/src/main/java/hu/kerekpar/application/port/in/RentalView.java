package hu.kerekpar.application.port.in;

import hu.kerekpar.domain.model.Rental;

/** A bérlés és a kiszámolt időtartam/díj a lekérdezés pillanatában. */
public record RentalView(Rental rental, long elapsedSeconds, long currentFee) {
}
