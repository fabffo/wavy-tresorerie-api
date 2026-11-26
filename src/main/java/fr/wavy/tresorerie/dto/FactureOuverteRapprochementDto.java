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
        String nomTiers,
        BigDecimal montantTotal,
        String statutPaiement,
        String typePiece,
        Long contratId
) {
    public FactureOuverteRapprochementDto(Long id, String typeFacture, Long tiersId, String numeroFacture,
            LocalDate dateFacture, LocalDate dateEcheance, BigDecimal resteAPayer, String libelle, String nomTiers,
            BigDecimal montantTotal, String statutPaiement, String typePiece) {
        this(id, typeFacture, tiersId, numeroFacture, dateFacture, dateEcheance, resteAPayer,
                libelle, nomTiers, montantTotal, statutPaiement, typePiece, null);
    }

    public FactureOuverteRapprochementDto(Long id, String typeFacture, Long tiersId, String numeroFacture,
            LocalDate dateFacture, LocalDate dateEcheance, BigDecimal resteAPayer, String libelle, String nomTiers) {
        this(id, typeFacture, tiersId, numeroFacture, dateFacture, dateEcheance, resteAPayer,
                libelle, nomTiers, null, null, null, null);
    }
}
