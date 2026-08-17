package fr.wavy.tresorerie.dto;

import fr.wavy.tresorerie.modele.SensOperation;

public record RegleRapprochementReponse(
        Long id,
        String libelle,
        String motif,
        SensOperation sens,
        Long tiersId,
        Integer priorite,
        boolean active
) {
}
