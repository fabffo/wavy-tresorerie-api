package fr.wavy.tresorerie.configuration;

import java.util.Arrays;
import java.util.List;

public record ContexteRequete(
        Long tenantId,
        Long utilisateurId,
        Long societeCouranteId,
        List<String> roles,
        String email
) {
    public ContexteRequete {
        roles = roles == null ? List.of() : roles.stream()
                .flatMap(role -> Arrays.stream(role.split(",")))
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    public boolean aRole(String role) {
        return roles != null && roles.contains(role);
    }
}
