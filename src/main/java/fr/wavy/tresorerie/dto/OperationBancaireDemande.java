package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.StatutOperationBancaire;
import fr.wavy.tresorerie.modele.TypeOperationBancaire;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OperationBancaireDemande(
        @NotNull(message = "Le compte bancaire est obligatoire.") Long compteBancaireId,
        Long importReleveBancaireId,
        @NotNull(message = "La date operation est obligatoire.") LocalDate dateOperation,
        LocalDate dateValeur,
        @NotBlank(message = "Le libelle est obligatoire.") String libelle,
        String referenceBancaire,
        BigDecimal debit,
        BigDecimal credit,
        @NotNull(message = "Le montant est obligatoire.") BigDecimal montant,
        @NotNull(message = "Le sens est obligatoire.") SensOperation sens,
        TypeOperationBancaire typeOperation,
        String tiersReconnu,
        TypeTiersReconnu typeTiersReconnu,
        NatureOperationBancaire natureOperation,
        StatutClassificationOperation statutClassification,
        StatutOperationBancaire statutRapprochement,
        Long tiersId,
        Long factureId,
        TypeFactureRapprochement typeFacture
) {
}
