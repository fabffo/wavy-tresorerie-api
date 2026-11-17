package fr.wavy.tresorerie.dto;

import fr.wavy.tresorerie.modele.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrats de l'assistant ; aucune proposition de consultation n'est persistée. */
public final class AssistantRapprochementDto {
    private AssistantRapprochementDto() {}
    public enum Etape { TIERS, NOTES_FRAIS, SALAIRES, CLIENTS, FOURNISSEURS, PIECES }
    public record Perimetre(String tiersReconnu, TypeTiersReconnu typeTiersReconnu,
            NatureOperationBancaire natureOperation, StatutClassificationOperation statutClassification,
            StatutOperationBancaire statutRapprochement, @Size(max=1000) List<@Positive Long> operationIds) {}
    public record Candidat(String cle, Long tiersId, String nomTiers, TypeTiersReconnu typeTiers,
            Long pieceId, String numeroPiece, String typePiece, BigDecimal montantDisponible,
            BigDecimal montantPropose, LocalDate datePiece, LocalDate dateEcheance, boolean sur,
            List<String> criteres, List<String> avertissements, NatureOperationBancaire natureProposee,
            PropositionCarteDto carte) {}
    public record Ligne(OperationBancaireReponse operation, List<Candidat> candidats,
            List<String> anomalies, boolean bloquee) {}
    public record Apercu(Etape etape, String categorie, int operationsPerimetre, int eligibles,
            int piecesDisponibles, int propositions, int anomalies, int traitees, int restantes,
            BigDecimal montantEligible, List<Ligne> lignes) {}
    public record Choix(@NotNull @Positive Long operationId, @NotBlank String candidatCle,
            Long pieceId, @NotNull @Positive Long tiersId, @NotNull @DecimalMin("0.00") BigDecimal montant,
            boolean confirmerNature, boolean verifierJustificatif, boolean confirmerAffectation) {}
    public record Confirmation(@NotNull Etape etape, @Valid Perimetre perimetre, String categorie,
            @AssertTrue @NotNull Boolean confirmation,
            @NotEmpty @Size(max=200) List<@NotNull @Valid Choix> selections) {}
    public record Resultat(Long operationId, String statut, String code, String message, Long rapprochementId) {}
    public record Bilan(int reussis, int refuses, int conflits, int aReprendre, List<Resultat> lignes) {}
}
