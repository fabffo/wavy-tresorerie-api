package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.util.List;

import fr.wavy.tresorerie.modele.StatutImportReleve;

public record ImportReleveResultatDto(
        Long importId,
        String nomFichier,
        Integer nombreLignes,
        Integer nombreLignesImportees,
        Integer nombreLignesRejetees,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        StatutImportReleve statutImport,
        List<String> erreurs
) {
}
