package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import fr.wavy.tresorerie.modele.StatutRapprochement;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import jakarta.validation.constraints.NotNull;

public record RapprochementBancaireDemande(
        @NotNull(message = "L'operation bancaire est obligatoire.") Long operationBancaireId,
        Long factureId,
        TypeFactureRapprochement typeFacture,
        @NotNull(message = "Le montant rapproche est obligatoire.") BigDecimal montantRapproche,
        BigDecimal ecart,
        StatutRapprochement statut,
        String commentaire
) {
}
