package fr.wavy.tresorerie.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmationCarteDemande(
        @NotNull @Positive Long factureId, @NotNull @Positive Long tiersConfirme,
        @NotNull @AssertTrue Boolean confirmation, Boolean confirmerAffectation, Boolean confirmerDateAtypique
) {}
