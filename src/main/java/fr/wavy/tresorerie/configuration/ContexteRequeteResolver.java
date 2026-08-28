package fr.wavy.tresorerie.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import fr.wavy.tresorerie.exception.RegleMetierException;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ContexteRequeteResolver {

    public ContexteRequete resoudre(HttpServletRequest request) {
        return new ContexteRequete(
                lireHeaderLong(request, "X-Tenant-Id"),
                lireHeaderLong(request, "X-Utilisateur-Id"),
                lireHeaderLong(request, "X-Societe-Courante-Id"),
                lireRoles(request),
                headerOptionnel(request, "X-Wavy-Email")
        );
    }

    private List<String> lireRoles(HttpServletRequest request) {
        String valeur = headerOptionnel(request, "X-Wavy-Roles", "X-Roles");
        if (valeur == null || valeur.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valeur.split(","))
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private Long lireHeaderLong(HttpServletRequest request, String nomHeader) {
        String valeur = request.getHeader(nomHeader);
        if (valeur == null || valeur.isBlank()) {
            throw new RegleMetierException("HEADER_MANQUANT", "Le header obligatoire " + nomHeader + " est manquant.");
        }
        try {
            return Long.valueOf(valeur);
        } catch (NumberFormatException exception) {
            throw new RegleMetierException("HEADER_INVALIDE", "Le header " + nomHeader + " doit etre un identifiant numerique.");
        }
    }

    private String headerOptionnel(HttpServletRequest request, String... noms) {
        for (String nom : noms) {
            String valeur = request.getHeader(nom);
            if (valeur != null && !valeur.isBlank()) {
                return valeur;
            }
        }
        return null;
    }

}
