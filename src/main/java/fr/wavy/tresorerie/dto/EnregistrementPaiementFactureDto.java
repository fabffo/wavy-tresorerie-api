package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnregistrementPaiementFactureDto(
        BigDecimal montant,
        LocalDate datePaiement,
        String libelleRapprochement,
        Long operationBancaireId, Long rapprochementId, Long tiersId, Boolean remboursementBeneficiaire
) {
    public EnregistrementPaiementFactureDto(BigDecimal montant, LocalDate datePaiement, String libelleRapprochement) {
        this(montant, datePaiement, libelleRapprochement, null, null, null, null);
    }
}
