package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompteBancaireDto(
        Long id,
        Long tenantId,
        Long societeInterneId,
        String nomCompte,
        String banque,
        String iban,
        String ibanMasque,
        String bic,
        String devise,
        BigDecimal soldeInitial,
        LocalDate dateSoldeInitial,
        BigDecimal soldeActuel,
        boolean actif,
        LocalDateTime dateCreation,
        LocalDateTime dateModification,
        String displayName
) {
}
