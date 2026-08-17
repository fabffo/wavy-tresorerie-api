package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record CompteBancaireModificationDto(
        @NotBlank(message = "Le nom du compte est obligatoire.") String nomCompte,
        @NotBlank(message = "La banque est obligatoire.") String banque,
        @NotBlank(message = "L'IBAN est obligatoire.") String iban,
        String bic,
        @NotBlank(message = "La devise est obligatoire.") String devise,
        BigDecimal soldeInitial,
        LocalDate dateSoldeInitial,
        Boolean actif
) {
}
