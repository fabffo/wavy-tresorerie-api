package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import fr.wavy.tresorerie.modele.TypePieceRapprochement;

public record PieceRapprochementReponse(
        Long id, String numero, TypePieceRapprochement type, Long tiersId, String tiers,
        LocalDate date, String libelle, BigDecimal montant, String statut,
        Long operationBancaireId, String libelleRapprochement
) {}
