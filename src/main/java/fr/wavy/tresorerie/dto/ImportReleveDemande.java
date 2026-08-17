package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import fr.wavy.tresorerie.modele.FormatImportReleve;
import fr.wavy.tresorerie.modele.StatutImportReleve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportReleveDemande(
        @NotNull(message = "Le compte bancaire est obligatoire.") Long compteBancaireId,
        @NotBlank(message = "Le nom du fichier est obligatoire.") String nomFichier,
        FormatImportReleve typeFichier,
        StatutImportReleve statutImport,
        Integer nombreLignes,
        Integer nombreLignesImportees,
        Integer nombreLignesRejetees,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        String messageErreur
) {
}
