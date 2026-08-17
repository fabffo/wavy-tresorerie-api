package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LigneRapprochementManuelDemande(
        @NotNull Long factureId,
        @NotNull TypeFactureRapprochement typeFacture,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montantRapproche
) {
}
