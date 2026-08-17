package fr.wavy.tresorerie.dto;

import java.util.List;

public record RapprochementPropositionsResultat(
        int propositionsTraitees,
        int validees,
        int annulees,
        int erreurs,
        List<String> messages
) {
}
