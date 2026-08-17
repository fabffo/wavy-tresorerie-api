package fr.wavy.tresorerie.dto;

import fr.wavy.tresorerie.modele.SensOperation;
import jakarta.validation.constraints.NotBlank;

public record RegleRapprochementDemande(
        @NotBlank(message = "Le libelle est obligatoire.") String libelle,
        String motif,
        SensOperation sens,
        Long tiersId,
        Integer priorite,
        Boolean active
) {
}
