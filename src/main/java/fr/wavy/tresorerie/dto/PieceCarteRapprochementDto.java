package fr.wavy.tresorerie.dto;

public record PieceCarteRapprochementDto(
        Long id, Long tenantId, Long societeInterneId, Long tiersId, String numeroPiece,
        java.time.LocalDate dateDepense, java.time.LocalDate datePiece, java.math.BigDecimal montant,
        String nomTiers, String nomMarchand, String libelle,
        java.time.LocalDate dateDepenseFiable, java.math.BigDecimal montantTtc
) {}
