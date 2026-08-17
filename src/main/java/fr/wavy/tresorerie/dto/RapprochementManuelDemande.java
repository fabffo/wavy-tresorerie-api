package fr.wavy.tresorerie.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record RapprochementManuelDemande(
        @NotEmpty List<@Valid LigneRapprochementManuelDemande> lignes
) {
}
