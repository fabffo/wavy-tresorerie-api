package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RapprochementExerciceAnterieurDemande(
        @NotNull @Positive Long tiersId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal montantDisponible,
        @NotNull @Min(1) Integer exerciceReference,
        @NotBlank @Size(max = 500) String motif,
        @NotNull @AssertTrue Boolean confirmation
) {}
