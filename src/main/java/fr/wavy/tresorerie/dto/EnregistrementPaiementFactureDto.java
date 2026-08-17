package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnregistrementPaiementFactureDto(
        BigDecimal montant,
        LocalDate datePaiement,
        String libelleRapprochement
) {
}
