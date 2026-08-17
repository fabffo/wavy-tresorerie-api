package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FactureEcheanceReponse(
        Long factureId,
        String numeroFacture,
        String typeFacture,
        String sensTresorerie,
        Long tiersId,
        String tiersRaisonSociale,
        Long contratId,
        String familleActivite,
        LocalDate dateFacture,
        LocalDate dateEcheance,
        BigDecimal montantTtc,
        BigDecimal montantDejaPaye,
        BigDecimal resteAPayer,
        long joursRetard,
        String statutEcheance
) {
}
