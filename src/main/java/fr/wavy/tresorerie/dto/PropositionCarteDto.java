package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PropositionCarteDto(
        Long operationId, Long factureId, Long tiersId, String numeroPiece, String libelleOperation,
        String nomTiers, LocalDate dateOperation, LocalDate dateAchat, LocalDate dateDepense,
        BigDecimal montantOperation, BigDecimal montantPiece, BigDecimal montantRapproche, BigDecimal ecart,
        boolean dateAtypique, boolean commercantReconnu, boolean affectationRequise,
        boolean ambiguite, boolean conflit, List<String> criteres
) {
    public PropositionCarteDto avecConflits(boolean ambiguite, boolean conflit) {
        return new PropositionCarteDto(operationId, factureId, tiersId, numeroPiece, libelleOperation, nomTiers,
                dateOperation, dateAchat, dateDepense, montantOperation, montantPiece, montantRapproche, ecart,
                dateAtypique, commercantReconnu, affectationRequise, ambiguite, conflit, criteres);
    }
}
