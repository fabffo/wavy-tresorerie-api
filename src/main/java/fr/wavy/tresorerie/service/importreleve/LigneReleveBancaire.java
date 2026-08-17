package fr.wavy.tresorerie.service.importreleve;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LigneReleveBancaire(
        int numeroLigne,
        LocalDate dateOperation,
        LocalDate dateValeur,
        String libelle,
        String referenceBancaire,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal montant
) {
}
