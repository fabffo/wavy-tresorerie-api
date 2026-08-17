package fr.wavy.tresorerie.dto;

import java.util.List;

public record RapprochementFacturesResultat(
        int operationsTraitees,
        int rapprochementsCrees,
        int propositionsCreees,
        int operationsSansMatch,
        List<String> messages
) {
}
