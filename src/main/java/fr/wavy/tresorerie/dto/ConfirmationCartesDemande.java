package fr.wavy.tresorerie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ConfirmationCartesDemande(
        @NotNull @AssertTrue Boolean confirmation,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Ligne> lignes) {
    public record Ligne(@NotNull @Positive Long operationId,
                        @NotNull @Valid ConfirmationCarteDemande demande) {}
}
