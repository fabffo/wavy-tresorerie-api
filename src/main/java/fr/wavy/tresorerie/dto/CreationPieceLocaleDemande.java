package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreationPieceLocaleDemande(
        @NotBlank @Size(max = 500) String libelle,
        @NotNull @Positive Long tiersId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal montantDisponible,
        @NotNull @AssertTrue Boolean confirmation,
        @Size(max = 500) String motifAbsenceJustificatif
) {
    public CreationPieceLocaleDemande(String libelle, Long tiersId, BigDecimal montantDisponible, Boolean confirmation) {
        this(libelle, tiersId, montantDisponible, confirmation, null);
    }
}
