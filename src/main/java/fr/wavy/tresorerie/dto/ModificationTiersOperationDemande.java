package fr.wavy.tresorerie.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ModificationTiersOperationDemande(
        @NotNull @Positive Long tiersActuelId,
        @NotNull @Positive Long tiersId
) {}
