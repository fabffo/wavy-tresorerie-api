package fr.wavy.tresorerie.dto;

import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import jakarta.validation.constraints.NotNull;

public record ClassificationOperationDemande(
        @NotNull(message = "La nature operation est obligatoire.") NatureOperationBancaire natureOperation
) {
}
