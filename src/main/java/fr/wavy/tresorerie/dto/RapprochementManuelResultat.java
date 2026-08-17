package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import fr.wavy.tresorerie.modele.StatutOperationBancaire;

public record RapprochementManuelResultat(
        Long operationId,
        BigDecimal montantOperation,
        BigDecimal montantRapproche,
        BigDecimal resteARapprocher,
        StatutOperationBancaire statutRapprochement
) {
}
