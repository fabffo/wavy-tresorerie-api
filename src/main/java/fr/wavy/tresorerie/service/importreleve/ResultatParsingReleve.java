package fr.wavy.tresorerie.service.importreleve;

import java.util.List;

public record ResultatParsingReleve(
        List<LigneReleveBancaire> lignes,
        List<String> erreurs,
        int nombreLignes
) {
}
