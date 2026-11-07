package fr.wavy.tresorerie.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fr.wavy.tresorerie.client.FacturesTresorerieClient;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.EnregistrementPaiementFactureDto;
import fr.wavy.tresorerie.dto.FactureOuverteRapprochementDto;
import fr.wavy.tresorerie.exception.RegleMetierException;
import fr.wavy.tresorerie.modele.*;
import fr.wavy.tresorerie.repository.*;

/** Réutilise les propositions comme intentions durables avant tout paiement interservice. */
@Service
public class PaiementAchatRapprochementService {
    @PersistenceContext private EntityManager em;
    private final OperationBancaireRepository operations;
    private final RapprochementBancaireRepository rapprochements;
    private final FacturesTresorerieClient factures;
    private final TransactionTemplate transaction;

    public PaiementAchatRapprochementService(OperationBancaireRepository operations,
            RapprochementBancaireRepository rapprochements, FacturesTresorerieClient factures,
            PlatformTransactionManager manager) {
        this.operations = operations; this.rapprochements = rapprochements; this.factures = factures;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }

    private OperationBancaire verrouiller(ContexteRequete c, Long id) {
        OperationBancaire op = operations.findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(id, c.tenantId(), c.societeCouranteId())
                .orElseThrow(() -> erreur("OPERATION_INTROUVABLE", "Opération bancaire introuvable."));
        em.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        return op;
    }

    public static boolean remboursement(OperationBancaire op) {
        String texte = op.getLibelle() == null ? "" : op.getLibelle().toUpperCase(java.util.Locale.ROOT);
        return op.getNatureOperation() == NatureOperationBancaire.NOTE_DE_FRAIS
                || op.getNatureOperation() == NatureOperationBancaire.REMBOURSEMENT
                || texte.contains("NDF") || texte.contains("NOTE FRAIS") || texte.contains("NOTE DE FRAIS")
                || texte.contains("REMBOURSEMENT");
    }

    public List<FactureOuverteRapprochementDto> candidates(ContexteRequete c, OperationBancaire op) {
        return remboursement(op)
                ? factures.listerFacturesAchatNotesFraisOuvertes(c, op.getTiersId(), null)
                : factures.listerFacturesOuvertes(c, TypeFactureRapprochement.ACHAT, op.getTiersId());
    }

    private void verifierOperation(OperationBancaire op) {
        if (op.isRapprochee() || op.getSens() != SensOperation.DEBIT || op.getTiersId() == null
                || (!remboursement(op) && op.getTypeTiersReconnu() != TypeTiersReconnu.FOURNISSEUR
                    && op.getTypeTiersReconnu() != TypeTiersReconnu.FOURNISSEUR_SERVICE))
            throw erreur("OPERATION_NON_ELIGIBLE", "Un fournisseur ou un bénéficiaire de remboursement est obligatoire.");
    }

    public Long preparer(ContexteRequete c, Long operationId, Long factureId, BigDecimal montant,
            int score, String libelle, String mode) {
        return tx(() -> {
            OperationBancaire op = verrouiller(c, operationId); verifierOperation(op);
            for (StatutRapprochement statut : List.of(StatutRapprochement.VALIDE, StatutRapprochement.PROPOSE)) {
                for (RapprochementBancaire r : rapprochements.findByOperationBancaireIdAndStatut(operationId, statut)) {
                    if (r.getTypeFacture() != TypeFactureRapprochement.ACHAT || !Objects.equals(r.getFactureId(), factureId)) continue;
                    if (statut == StatutRapprochement.VALIDE) throw erreur("RAPPROCHEMENT_DEJA_VALIDE", "Ce couple opération–facture est déjà validé.");
                    if (r.getMontantRapproche().compareTo(montant) != 0)
                        throw erreur("REPRISE_DIFFERENTE", "La proposition existante porte un autre montant.");
                    return r.getId();
                }
            }
            verifierDisponible(op, montant, null);
            var facture = candidates(c, op).stream().filter(f -> factureId.equals(f.id())).findFirst()
                    .orElseThrow(() -> erreur("FACTURE_NON_CANDIDATE", "Facture validée, tiers et type de pièce cohérents obligatoires."));
            if (montant.compareTo(facture.resteAPayer()) > 0)
                throw erreur("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant dépasse le reste à payer.");
            RapprochementBancaire r = new RapprochementBancaire();
            r.setTenantId(c.tenantId()); r.setSocieteInterneId(c.societeCouranteId()); r.setOperationBancaire(op);
            r.setFactureId(factureId); r.setTypeFacture(TypeFactureRapprochement.ACHAT); r.setTypePiece(TypePieceRapprochement.FACTURE_ACHAT);
            r.setDocumentAssocieNumero(facture.numeroFacture()); r.setDocumentAssocieLibelle(facture.libelle());
            r.setMontantRapproche(montant); r.setEcart(BigDecimal.ZERO); r.setScoreMatching(score);
            r.setLibelleRapprochement(libelle); r.setModeRapprochement(mode); r.setStatut(StatutRapprochement.PROPOSE);
            r.setDateRapprochement(LocalDateTime.now()); r.setDateProposition(LocalDateTime.now()); r.setUtilisateurId(c.utilisateurId());
            return rapprochements.saveAndFlush(r).getId();
        });
    }

    private RapprochementBancaire trouver(ContexteRequete c, Long id) {
        return rapprochements.findByIdAndTenantIdAndSocieteInterneId(id, c.tenantId(), c.societeCouranteId())
                .orElseThrow(() -> erreur("RAPPROCHEMENT_INTROUVABLE", "Rapprochement introuvable."));
    }

    private void verifierDisponible(OperationBancaire op, BigDecimal montant, Long exclure) {
        if (montant == null || montant.signum() <= 0 || montant.scale() > 2)
            throw erreur("MONTANT_INVALIDE", "Un montant positif à deux décimales est obligatoire.");
        BigDecimal reserve = rapprochements.sommeMontantParOperationEtStatut(op.getId(), StatutRapprochement.VALIDE);
        for (var r : rapprochements.findByOperationBancaireIdAndStatut(op.getId(), StatutRapprochement.PROPOSE))
            if (r.isPaiementEngage() && !Objects.equals(r.getId(), exclure)) reserve = reserve.add(r.getMontantRapproche());
        if (reserve.add(montant).compareTo(montantOperation(op)) > 0)
            throw erreur("MONTANT_SUPERIEUR_OPERATION", "Le montant dépasse le disponible bancaire.");
    }

    public void valider(ContexteRequete c, Long id) {
        // Commit de l'intention AVANT le HTTP : une réponse perdue reste rejouable avec le même identifiant.
        tx(() -> {
            var r = trouver(c, id); var op = verrouiller(c, r.getOperationBancaire().getId()); em.refresh(r);
            verifierProposition(r); verifierOperation(op); verifierDisponible(op, r.getMontantRapproche(), id);
            if (rapprochements.existsByOperationBancaireIdAndFactureIdAndStatutIn(op.getId(), r.getFactureId(), List.of(StatutRapprochement.VALIDE)))
                throw erreur("RAPPROCHEMENT_DEJA_VALIDE", "Ce couple opération–facture est déjà validé.");
            if (!r.isPaiementEngage()) {
                var facture = candidates(c, op).stream().filter(f -> r.getFactureId().equals(f.id())).findFirst()
                        .orElseThrow(() -> erreur("FACTURE_NON_CANDIDATE", "La facture n'est plus validée ou éligible."));
                if (r.getMontantRapproche().compareTo(facture.resteAPayer()) > 0)
                    throw erreur("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant dépasse le reste à payer.");
            }
            r.setPaiementEngage(true); return null;
        });
        tx(() -> {
            var r = trouver(c, id); var op = verrouiller(c, r.getOperationBancaire().getId()); em.refresh(r);
            verifierProposition(r); verifierOperation(op); verifierDisponible(op, r.getMontantRapproche(), id);
            factures.enregistrerPaiement(c, TypeFactureRapprochement.ACHAT, r.getFactureId(),
                    new EnregistrementPaiementFactureDto(r.getMontantRapproche(), op.getDateOperation(),
                            r.getLibelleRapprochement(), op.getId(), r.getId(), op.getTiersId(), remboursement(op)));
            r.setStatut(StatutRapprochement.VALIDE); r.setDateValidation(LocalDateTime.now()); r.setUtilisateurValidationId(c.utilisateurId());
            BigDecimal total = rapprochements.sommeMontantParOperationEtStatut(op.getId(), StatutRapprochement.VALIDE);
            // La requête déclenche le flush du passage à VALIDE.
            boolean complet = total.compareTo(montantOperation(op)) == 0;
            op.setRapprochee(complet); op.setStatutRapprochement(complet ? StatutOperationBancaire.RAPPROCHEE : StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
            op.setFactureId(r.getFactureId()); op.setTypeFacture(TypeFactureRapprochement.ACHAT);
            op.setLibelleRapprochement(r.getLibelleRapprochement()); op.setDateRapprochement(LocalDateTime.now()); op.setDateModification(LocalDateTime.now());
            return null;
        });
    }

    public void annuler(ContexteRequete c, Long id, String motif) {
        tx(() -> {
            var r = trouver(c, id); verrouiller(c, r.getOperationBancaire().getId()); em.refresh(r); verifierProposition(r);
            if (r.isPaiementEngage()) throw erreur("PAIEMENT_A_REPRENDRE", "Le résultat du paiement doit être repris, pas annulé.");
            r.setStatut(StatutRapprochement.ANNULE); r.setDateAnnulation(LocalDateTime.now());
            r.setUtilisateurAnnulationId(c.utilisateurId()); r.setMotifAnnulation(motif); return null;
        });
    }

    private void verifierProposition(RapprochementBancaire r) {
        if (r.getStatut() != StatutRapprochement.PROPOSE || r.getTypeFacture() != TypeFactureRapprochement.ACHAT)
            throw erreur("RAPPROCHEMENT_STATUT_INVALIDE", "Seule une proposition achat peut être validée.");
    }
    private BigDecimal montantOperation(OperationBancaire op) { return (op.getDebit() == null ? op.getMontant() : op.getDebit()).abs(); }
    private RegleMetierException erreur(String code, String message) { return new RegleMetierException(code, message); }
}
