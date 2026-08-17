package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FactureOuverteRapprochementDto(
        Long id,
        String typeFacture,
        Long tiersId,
        String numeroFacture,
        LocalDate dateFacture,
        LocalDate dateEcheance,
        BigDecimal resteAPayer,
        String libelle,
        String nomTiers
) {
}
