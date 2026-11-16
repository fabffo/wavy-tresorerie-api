package fr.wavy.tresorerie.dto;

import java.util.List;

public record ConfirmationCartesResultat(int reussis, int refuses, int conflits, List<Ligne> lignes) {
    public record Ligne(Long operationId, Long factureId, String statut,
                        Long rapprochementId, String code, String message) {}
}
