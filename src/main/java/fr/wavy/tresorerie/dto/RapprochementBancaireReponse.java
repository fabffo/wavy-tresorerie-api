package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import fr.wavy.tresorerie.modele.StatutRapprochement;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import fr.wavy.tresorerie.modele.TypePieceRapprochement;

public record RapprochementBancaireReponse(
        Long id,
        Long operationBancaireId,
        String operationLibelle,
        String tiersReconnu,
        Long factureId,
        TypeFactureRapprochement typeFacture,
        Long salaireId,
        String periodeSalaire,
        String matriculeSalarie,
        Long pieceRapprochementId,
        TypePieceRapprochement typePiece,
        String documentAssocieNumero,
        String documentAssocieLibelle,
        BigDecimal montantOperation,
        BigDecimal montantRapproche,
        BigDecimal ecart,
        StatutRapprochement statut,
        String modeRapprochement,
        String libelleRapprochement,
        Integer scoreMatching,
        String commentaire,
        Integer exerciceReference,
        LocalDateTime dateRapprochement,
        Long utilisateurId,
        LocalDateTime dateProposition,
        LocalDateTime dateValidation,
        Long utilisateurValidationId,
        LocalDateTime dateAnnulation,
        Long utilisateurAnnulationId,
        String motifAnnulation
) {
}
