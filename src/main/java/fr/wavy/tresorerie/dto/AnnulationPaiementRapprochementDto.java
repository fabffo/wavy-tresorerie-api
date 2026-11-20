package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

public record AnnulationPaiementRapprochementDto(
        Long rapprochementId, Long operationBancaireId,
        BigDecimal montant, String reference
) {}
