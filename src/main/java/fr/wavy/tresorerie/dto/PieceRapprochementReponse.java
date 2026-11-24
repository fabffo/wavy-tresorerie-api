package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.wavy.tresorerie.modele.TypePieceRapprochement;
import fr.wavy.tresorerie.modele.SensOperation;

public record PieceRapprochementReponse(
        Long id, String numero, TypePieceRapprochement type, Long tiersId, String tiers,
        LocalDate date, String libelle, BigDecimal montant, String statut,
        Long operationBancaireId, String libelleRapprochement,
        Long societeInterneId, SensOperation sensBancaire, String motif,
        boolean sansJustificatifOriginal, BigDecimal tvaDeductible
) {
    @JsonProperty
    public YearMonth periodeComptableEnvisagee() {
        return sansJustificatifOriginal ? YearMonth.from(date) : null;
    }
}
