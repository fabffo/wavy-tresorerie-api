package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

public record DashboardTresorerieReponse(
        long nombreComptes,
        long operationsARapprocher,
        long rapprochementsValides,
        BigDecimal soldeTotal,
        BigDecimal encaissements,
        BigDecimal decaissements
) {
}
