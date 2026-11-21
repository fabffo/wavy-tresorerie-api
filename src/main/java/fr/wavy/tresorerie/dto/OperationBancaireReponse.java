package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;

public record OperationBancaireReponse(
        Long id,
        Long compteBancaireId,
        String compteBancaireLibelle,
        Long importReleveBancaireId,
        LocalDate dateOperation,
        LocalDate dateValeur,
        String libelle,
        String referenceBancaire,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal montant,
        SensOperation sens,
        TypeOperationBancaire typeOperation,
        String tiersReconnu,
        TypeTiersReconnu typeTiersReconnu,
        NatureOperationBancaire natureOperation,
        StatutClassificationOperation statutClassification,
        StatutOperationBancaire statutRapprochement,
        boolean rapprochee,
        Integer numeroLigneImport,
        String hashOperation,
        Long tiersId,
        Long factureId,
        TypeFactureRapprochement typeFacture,
        Long salaireId,
        Long factureRapprochementId,
        Boolean pieceRapprochementAutorisee,
        String libelleRapprochement,
        LocalDateTime dateRapprochement,
        BigDecimal montantRapproche,
        String numeroPieceAssociee,
        Long pieceRapprochementId,
        List<String> rolesTiers
) {
}
