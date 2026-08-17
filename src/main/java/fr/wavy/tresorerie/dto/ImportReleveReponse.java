package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import fr.wavy.tresorerie.modele.FormatImportReleve;
import fr.wavy.tresorerie.modele.StatutImportReleve;

public record ImportReleveReponse(
        Long id,
        Long compteBancaireId,
        String compteBancaireLibelle,
        String nomFichier,
        FormatImportReleve typeFichier,
        StatutImportReleve statutImport,
        Integer nombreLignes,
        Integer nombreLignesImportees,
        Integer nombreLignesRejetees,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        LocalDateTime dateImport,
        String messageErreur
) {
}
