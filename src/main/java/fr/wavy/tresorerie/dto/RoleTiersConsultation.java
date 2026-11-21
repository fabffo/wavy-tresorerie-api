package fr.wavy.tresorerie.dto;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record RoleTiersConsultation(String code, String libelle) {
    public static RoleTiersConsultation depuisCode(String code) {
        String libelle = switch (code) {
            case "SALARIE" -> "Salarié";
            case "PORTEUR_CARTE" -> "Porteur de carte";
            case "FOURNISSEUR_GENERAL" -> "Fournisseur général";
            case "FOURNISSEUR_SERVICES" -> "Fournisseur de services";
            case "GESTIONNAIRE_NOTE_FRAIS" -> "Gestionnaire de notes de frais";
            default -> code.substring(0, 1) + code.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        return new RoleTiersConsultation(code, libelle);
    }

    public static Comparator<String> triLibelles() {
        return Comparator.comparing((String role) -> role, Collator.getInstance(Locale.FRENCH))
                .thenComparing(Comparator.naturalOrder());
    }

    public static String libelles(List<String> codes) {
        return codes.stream().map(RoleTiersConsultation::depuisCode).map(RoleTiersConsultation::libelle)
                .collect(Collectors.joining(", "));
    }
}
