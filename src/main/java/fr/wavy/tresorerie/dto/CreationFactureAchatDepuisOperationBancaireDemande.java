package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreationFactureAchatDepuisOperationBancaireDemande(
        Long operationBancaireId,
        Long tiersId,
        String fournisseurNom,
        LocalDate dateOperation,
        String libelleOperation,
        BigDecimal montantTtc
) {
}
