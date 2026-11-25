package fr.wavy.tresorerie.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import fr.wavy.tresorerie.dto.ConfirmationCartesDemande;
import fr.wavy.tresorerie.dto.ConfirmationCartesResultat;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fr.wavy.tresorerie.dto.ConfirmationCarteDemande;
import fr.wavy.tresorerie.client.FacturesTresorerieClient;
import fr.wavy.tresorerie.client.TiersTresorerieClient;
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
    private final TiersTresorerieClient tiers;
    private final TransactionTemplate transaction;

    public PaiementAchatRapprochementService(OperationBancaireRepository operations,
            RapprochementBancaireRepository rapprochements, FacturesTresorerieClient factures,
            TiersTresorerieClient tiers, PlatformTransactionManager manager) {
        this.operations = operations; this.rapprochements = rapprochements; this.factures = factures; this.tiers = tiers;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }

    private OperationBancaire verrouiller(ContexteRequete c, Long id) {
        // Verrou sur la seule opération : éviter le follow-on locking des associations
        // eager lors d'un refresh Hibernate, notamment pour deux cartes du même compte.
        var ids = em.createNativeQuery("select id from operation_bancaire where id = :id and tenant_id = :tenant "
                + "and societe_interne_id = :societe and actif = true for update")
                .setParameter("id", id).setParameter("tenant", c.tenantId()).setParameter("societe", c.societeCouranteId()).getResultList();
        if (ids.isEmpty()) throw erreur("OPERATION_INTROUVABLE", "Opération bancaire introuvable.");
        OperationBancaire op = operations.findByIdAndTenantIdAndSocieteInterneIdAndActifTrue(id, c.tenantId(), c.societeCouranteId())
                .orElseThrow(() -> erreur("OPERATION_INTROUVABLE", "Opération bancaire introuvable."));
        em.refresh(op);
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
        return candidates(c, op, null);
    }

    static final String MANUEL_REMBOURSEMENT = "MANUEL_REMBOURSEMENT";
    static final String MANUEL_FOURNISSEUR = "MANUEL_FOURNISSEUR";
    static final String MANUEL_CARTE = "MANUEL_CARTE";

    static final String ROLE_ACHAT = "ROLE_ACHAT";
    static final String ROLE_REMBOURSEMENT = "ROLE_REMBOURSEMENT";
    static final String ROLE_CARTE = "ROLE_CARTE";

    static boolean carte(String mode) {
        return "CARTE_ENTREPRISE".equals(mode) || MANUEL_CARTE.equals(mode) || ROLE_CARTE.equals(mode);
    }

    private static boolean remboursement(OperationBancaire op, String mode) {
        return ROLE_REMBOURSEMENT.equals(mode) || MANUEL_REMBOURSEMENT.equals(mode)
                || !carte(mode) && !MANUEL_FOURNISSEUR.equals(mode) && !ROLE_ACHAT.equals(mode) && remboursement(op);
    }

    private List<FactureOuverteRapprochementDto> candidates(ContexteRequete c, OperationBancaire op, String mode) {
        if (remboursement(op, mode))
            return factures.listerFacturesAchatNotesFraisOuvertes(c, op.getTiersId(), null);
        var ouvertes=factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,op.getTiersId());
        if (MANUEL_CARTE.equals(mode) || ROLE_CARTE.equals(mode)) return ouvertes.stream()
                .filter(f -> "DEPENSE_CARTE_ENTREPRISE".equals(f.typePiece())).toList();
        if (carte(mode)) return ouvertes;
        var cartes=factures.listerCartesOuvertes(c).stream().map(fr.wavy.tresorerie.dto.PieceCarteRapprochementDto::id).collect(java.util.stream.Collectors.toSet());
        return ouvertes.stream().filter(f -> !cartes.contains(f.id()) && (!ROLE_ACHAT.equals(mode) || !"DEPENSE_CARTE_ENTREPRISE".equals(f.typePiece()))).toList();
    }

    private void verifierOperation(ContexteRequete c, OperationBancaire op, String mode) {
        if (op.isRapprochee() || op.getSens()!=SensOperation.DEBIT || op.getTiersId()==null)
            throw erreur("OPERATION_NON_ELIGIBLE", "Une opération débitrice avec un solde et un tiers est requise.");
        if (mode != null && mode.startsWith("ROLE_")) {
            boolean compatible = tiers.listerTiersActifs(c).stream().anyMatch(t -> op.getTiersId().equals(t.id())
                    && !Boolean.FALSE.equals(t.actif()) && t.roles() != null && switch (mode) {
                        case ROLE_REMBOURSEMENT -> t.roles().contains("SALARIE") && op.getTypeOperation()==TypeOperationBancaire.VIREMENT_EMIS;
                        case ROLE_CARTE -> t.roles().contains("MARCHAND") && op.getTypeOperation()==TypeOperationBancaire.PAIEMENT_CB;
                        case ROLE_ACHAT -> ClassificationOperationBancaireService.roleFournisseur(t.roles());
                        default -> false;
                    });
            if (!compatible || op.getStatutRapprochement() == StatutOperationBancaire.IGNOREE)
                throw erreur("OPERATION_NON_ELIGIBLE", "Rôle actif et mouvement bancaire compatibles obligatoires.");
            return;
        }
        if (MANUEL_REMBOURSEMENT.equals(mode) || MANUEL_FOURNISSEUR.equals(mode) || MANUEL_CARTE.equals(mode)) {
            boolean compatible = tiers.listerTiersActifs(c).stream().anyMatch(t -> op.getTiersId().equals(t.id())
                    && !Boolean.FALSE.equals(t.actif()) && t.roles() != null
                    && (MANUEL_REMBOURSEMENT.equals(mode) ? t.roles().contains("SALARIE")
                            : ClassificationOperationBancaireService.roleFournisseur(t.roles())));
            if (!compatible || op.getStatutRapprochement() == StatutOperationBancaire.IGNOREE)
                throw erreur("OPERATION_NON_ELIGIBLE", "Tiers actif de rôle compatible et opération non ignorée obligatoires.");
            return;
        }
        if (remboursement(op) || op.getTypeTiersReconnu()==TypeTiersReconnu.FOURNISSEUR
                || op.getTypeTiersReconnu()==TypeTiersReconnu.FOURNISSEUR_SERVICE) return;
        if ("ASSISTANT".equals(mode) && tiers.listerTiersActifs(c).stream().anyMatch(t -> op.getTiersId().equals(t.id())
                && !Boolean.FALSE.equals(t.actif()) && ClassificationOperationBancaireService.roleFournisseur(t.roles()))) return;
        throw erreur("OPERATION_NON_ELIGIBLE", "Un fournisseur ou un bénéficiaire de remboursement est obligatoire.");
    }

    public Long preparer(ContexteRequete c, Long operationId, Long factureId, BigDecimal montant,
            int score, String libelle, String mode) {
        return tx(() -> preparerVerrouille(c, verrouiller(c, operationId), factureId, montant, score, libelle, mode));
    }

    public void confirmerCarteManuellement(ContexteRequete c, Long operationId, Long factureId, BigDecimal montant) {
        confirmerCarteParRole(c, operationId, factureId, montant, false);
    }

    public Long confirmerCarteFournisseurs(ContexteRequete c, Long operationId, Long factureId, BigDecimal montant) {
        return confirmerCarteParRole(c, operationId, factureId, montant, true);
    }

    private Long confirmerCarteParRole(ContexteRequete c, Long operationId, Long factureId, BigDecimal montant, boolean automatique) {
        Long id = tx(() -> {
            var op = verrouiller(c, operationId);
            verrouillerPieceCarte(c, factureId);
            verifierReservationCarte(c, operationId, factureId);
            if (automatique) {
                boolean recherchePieces = tiers.listerTiersActifs(c).stream()
                        .anyMatch(t -> t.id().equals(op.getTiersId()) && !Boolean.FALSE.equals(t.actif())
                                && !Boolean.TRUE.equals(t.pieceRapprochementAutorisee()));
                if (!recherchePieces) throw erreur("CARTE_NON_ELIGIBLE", "L’autorisation du tiers a changé.");
                var autres = operations.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                        c.tenantId(), c.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE);
                var traces = rapprochements.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(), c.societeCouranteId());
                boolean certaine = RapprochementCarteMatcher.propositionsFournisseurs(op, factures.listerCartesOuvertes(c), autres, traces)
                        .stream().anyMatch(p -> factureId.equals(p.factureId()) && !p.conflit() && montant.compareTo(p.montantRapproche()) == 0);
                if (!certaine) throw erreur("CARTE_NON_ELIGIBLE", "La correspondance carte n’est plus certaine. Actualisez les propositions.");
            }
            return preparerVerrouille(c, op, factureId, montant, 0,
                    "CARTE-OP-" + operationId + "-FA-" + factureId, ROLE_CARTE);
        });
        valider(c, id);
        return id;
    }

    private void verifierReservationCarte(ContexteRequete c, Long operationId, Long factureId) {
        boolean reservee = rapprochements.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(), c.societeCouranteId())
                .stream().anyMatch(r -> r.getStatut() != StatutRapprochement.ANNULE
                        && (operationId.equals(r.getOperationBancaire().getId())
                            || r.getTypeFacture() == TypeFactureRapprochement.ACHAT && factureId.equals(r.getFactureId())));
        if (reservee) throw erreur("CONFLIT_CARTE", "Une autre proposition ou un rapprochement réserve cette opération ou cette pièce.");
    }

    private void verrouillerPieceCarte(ContexteRequete c, Long factureId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:cle, 0))")
                .setParameter("cle", "carte:" + c.tenantId() + ":" + c.societeCouranteId() + ":" + factureId).getSingleResult();
    }

    private Long preparerVerrouille(ContexteRequete c, OperationBancaire op, Long factureId, BigDecimal montant,
            int score, String libelle, String mode) {
        verifierOperation(c, op, mode);
        for (StatutRapprochement statut : List.of(StatutRapprochement.VALIDE, StatutRapprochement.PROPOSE)) {
            for (RapprochementBancaire r : rapprochements.findByOperationBancaireIdAndStatut(op.getId(), statut)) {
                if (carte(r.getModeRapprochement()) != carte(mode))
                    throw erreur("CONFLIT_NATURE", "Une affectation carte et une affectation sur facture classique ne peuvent pas se cumuler.");
                if (r.getTypeFacture() != TypeFactureRapprochement.ACHAT || !Objects.equals(r.getFactureId(), factureId)) continue;
                if (statut == StatutRapprochement.VALIDE) throw erreur("RAPPROCHEMENT_DEJA_VALIDE", "Ce couple opération–facture est déjà validé.");
                if (r.getMontantRapproche().compareTo(montant) != 0)
                    throw erreur("REPRISE_DIFFERENTE", "La proposition existante porte un autre montant.");
                if (mode != null && mode.startsWith("ROLE_") && !mode.equals(r.getModeRapprochement())) {
                    if (r.isPaiementEngage())
                        throw erreur("PAIEMENT_A_REPRENDRE", "Reprenez le paiement engagé avec ses références d’origine.");
                    var piece = candidates(c, op, mode).stream().filter(f -> factureId.equals(f.id())
                            && op.getTiersId().equals(f.tiersId()) && montant.compareTo(f.resteAPayer()) <= 0).findFirst()
                            .orElseThrow(() -> erreur("FACTURE_NON_CANDIDATE", "La pièce n’est plus ouverte ou compatible."));
                    r.setModeRapprochement(mode);
                    r.setTypePiece(typePiece(mode, piece));
                }
                return r.getId();
            }
        }
        verifierDisponible(op, montant, null);
        var facture = candidates(c, op, mode).stream().filter(f -> factureId.equals(f.id()) && op.getTiersId().equals(f.tiersId())).findFirst()
                .orElseThrow(() -> erreur("FACTURE_NON_CANDIDATE", "Facture validée, tiers et type de pièce cohérents obligatoires."));
        if (montant.compareTo(facture.resteAPayer()) > 0)
            throw erreur("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant dépasse le reste à payer.");
        if ((MANUEL_CARTE.equals(mode) || ROLE_CARTE.equals(mode)) && (montantOperation(op).subtract(facture.resteAPayer()).abs()
                .compareTo(new BigDecimal("0.01")) > 0 || montant.compareTo(montantOperation(op).min(facture.resteAPayer())) != 0))
            throw erreur("MONTANT_CARTE_INCOMPATIBLE", "Le montant carte doit correspondre au disponible à un centime près.");

        RapprochementBancaire r = new RapprochementBancaire();
        r.setTenantId(c.tenantId()); r.setSocieteInterneId(c.societeCouranteId()); r.setOperationBancaire(op);
        r.setFactureId(factureId); r.setTypeFacture(TypeFactureRapprochement.ACHAT); r.setTypePiece(typePiece(mode, facture));
        r.setDocumentAssocieNumero(facture.numeroFacture()); r.setDocumentAssocieLibelle(facture.libelle());
        r.setMontantRapproche(montant); r.setEcart("CARTE_ENTREPRISE".equals(mode) ? montantOperation(op).subtract(montant).abs() : BigDecimal.ZERO); r.setScoreMatching(score);
        r.setLibelleRapprochement(libelle); r.setModeRapprochement(mode); r.setStatut(StatutRapprochement.PROPOSE);
        r.setDateRapprochement(LocalDateTime.now()); r.setDateProposition(LocalDateTime.now()); r.setUtilisateurId(c.utilisateurId());
        return rapprochements.saveAndFlush(r).getId();
    }

    /** Pas de transaction de lot : chaque ligne conserve les transactions et reçus existants. */
    public ConfirmationCartesResultat confirmerCartes(ContexteRequete c, ConfirmationCartesDemande lot) {
        if (lot == null || !Boolean.TRUE.equals(lot.confirmation()) || lot.lignes() == null
                || lot.lignes().isEmpty() || lot.lignes().size() > 200)
            throw erreur("CONFIRMATION_CARTE_REQUISE", "Confirmez un lot de 1 à 200 rapprochements.");
        Map<Long, Integer> operationsDemandees = new HashMap<>(), piecesDemandees = new HashMap<>();
        for (var ligne : lot.lignes()) {
            if (ligne == null || ligne.operationId() == null || ligne.demande() == null || ligne.demande().factureId() == null)
                throw erreur("DEMANDE_CARTE_INVALIDE", "Une opération et une pièce sont obligatoires pour chaque ligne.");
            operationsDemandees.merge(ligne.operationId(), 1, Integer::sum);
            piecesDemandees.merge(ligne.demande().factureId(), 1, Integer::sum);
        }
        var resultats = new ArrayList<ConfirmationCartesResultat.Ligne>();
        for (var ligne : lot.lignes()) {
            Long operation = ligne.operationId(), piece = ligne.demande().factureId();
            try {
                // Refuser toutes les occurrences : ne pas choisir arbitrairement la première.
                if (operationsDemandees.get(operation) > 1 || piecesDemandees.get(piece) > 1)
                    throw erreur("DOUBLON_CARTE", "Une opération ou une pièce apparaît plusieurs fois dans le lot.");
                Long id = confirmerCarte(c, operation, ligne.demande(), true);
                resultats.add(new ConfirmationCartesResultat.Ligne(operation, piece, "SUCCES", id, null, "Rapprochement confirmé."));
            } catch (RegleMetierException e) {
                boolean conflit = java.util.Set.of("DOUBLON_CARTE", "CONFLIT_CARTE", "TIERS_CARTE_DIFFERENT", "AMBIGUITE_CARTE").contains(e.getCode());
                resultats.add(new ConfirmationCartesResultat.Ligne(operation, piece, conflit ? "CONFLIT" : "REFUSE", null, e.getCode(), e.getMessage()));
            } catch (RuntimeException e) {
                // Un appel distant peut avoir abouti : conserver l'intention et sa clé idempotente.
                resultats.add(new ConfirmationCartesResultat.Ligne(operation, piece, "REFUSE", null,
                        "RESULTAT_A_REPRENDRE", "Résultat non confirmé. Réessayez ce même couple : la reprise est idempotente."));
            }
        }
        return new ConfirmationCartesResultat((int) resultats.stream().filter(l -> l.statut().equals("SUCCES")).count(),
                (int) resultats.stream().filter(l -> l.statut().equals("REFUSE")).count(),
                (int) resultats.stream().filter(l -> l.statut().equals("CONFLIT")).count(), List.copyOf(resultats));
    }

    /** L'association du tiers et la classification ne sont faites qu'après consentement explicite. */
    public Long confirmerCarte(ContexteRequete c, Long operationId, ConfirmationCarteDemande demande) {
        return confirmerCarte(c, operationId, demande, false);
    }

    private Long confirmerCarte(ContexteRequete c, Long operationId, ConfirmationCarteDemande demande, boolean groupe) {
        if (demande == null || !Boolean.TRUE.equals(demande.confirmation()) || demande.factureId() == null || demande.tiersConfirme() == null)
            throw erreur("CONFIRMATION_CARTE_REQUISE", "Confirmation de la pièce et de son fournisseur obligatoire.");
        Long id = tx(() -> {
            OperationBancaire op = verrouiller(c, operationId);
            // Même verrou transactionnel PostgreSQL que les reçus Factures : deux opérations
            // ne peuvent pas réserver simultanément la même pièce dans ce parcours.
            verrouillerPieceCarte(c, demande.factureId());
            var enCours = rapprochements.findByTenantIdAndSocieteInterneIdOrderByDateRapprochementDesc(c.tenantId(), c.societeCouranteId());
            for (var r : enCours) {
                if (r.getStatut() != StatutRapprochement.PROPOSE && r.getStatut() != StatutRapprochement.VALIDE) continue;
                boolean memeOperation = op.getId().equals(r.getOperationBancaire().getId());
                boolean memePiece = r.getTypeFacture() == TypeFactureRapprochement.ACHAT && demande.factureId().equals(r.getFactureId());
                if (memeOperation && memePiece && "CARTE_ENTREPRISE".equals(r.getModeRapprochement())) {
                    if (!demande.tiersConfirme().equals(op.getTiersId()))
                        throw erreur("TIERS_CARTE_DIFFERENT", "Le tiers confirmé diffère du tiers déjà associé.");
                    return r.getId(); // reprise, y compris après une réponse HTTP perdue
                }
                if (memeOperation || memePiece)
                    throw erreur("CONFLIT_CARTE", "Une autre proposition ou un rapprochement réserve cette opération ou cette pièce.");
            }
            var piecesOuvertes = factures.listerCartesOuvertes(c);
            var piece = piecesOuvertes.stream().filter(p -> demande.factureId().equals(p.id())).findFirst()
                    .orElseThrow(() -> erreur("PIECE_CARTE_INDISPONIBLE", "La pièce carte n'est plus ouverte dans cette société."));
            var candidat = RapprochementCarteMatcher.comparer(op, piece)
                    .orElseThrow(() -> erreur("CARTE_NON_ELIGIBLE", "Opération, société ou montant non éligible à cette pièce carte."));
            if (candidat.conflit() || !demande.tiersConfirme().equals(piece.tiersId()))
                throw erreur("TIERS_CARTE_DIFFERENT", "Le fournisseur confirmé doit correspondre à la pièce et au tiers bancaire existant.");
            if (groupe) {
                if (candidat.ecart().signum() != 0 || candidat.dateAtypique() || !candidat.commercantReconnu())
                    throw erreur("VERIFICATION_INDIVIDUELLE_CARTE", "Montant, dates ou commerçant à vérifier individuellement avec le justificatif.");
                var autresOperations = operations.findByTenantIdAndSocieteInterneIdAndActifTrueAndStatutRapprochementOrderByDateOperationDescIdDesc(
                        c.tenantId(), c.societeCouranteId(), StatutOperationBancaire.NON_RAPPROCHEE);
                if (piecesOuvertes.stream().anyMatch(p -> !p.id().equals(piece.id()) && RapprochementCarteMatcher.comparer(op, p).isPresent())
                        || autresOperations.stream().anyMatch(o -> !o.getId().equals(op.getId()) && RapprochementCarteMatcher.comparer(o, piece).isPresent()))
                    throw erreur("AMBIGUITE_CARTE", "Plusieurs correspondances existent : vérifiez et confirmez cette ligne individuellement.");
            }
            if (candidat.dateAtypique() && !Boolean.TRUE.equals(demande.confirmerDateAtypique()))
                throw erreur("DATE_CARTE_ATYPIQUE", "Vérifiez le justificatif et confirmez explicitement la date atypique.");
            if (candidat.affectationRequise()) {
                if (!Boolean.TRUE.equals(demande.confirmerAffectation()))
                    throw erreur("AFFECTATION_CARTE_REQUISE", "Confirmez explicitement l'association du fournisseur et la classification en achat.");
                op.setTiersId(piece.tiersId()); op.setTiersReconnu(piece.nomTiers());
                op.setTypeTiersReconnu(TypeTiersReconnu.FOURNISSEUR);
                if (op.getStatutClassification()!=StatutClassificationOperation.CLASSEE_MANUELLE) {
                    op.setNatureOperation(NatureOperationBancaire.FACTURE_FOURNISSEUR);
                    op.setStatutClassification(StatutClassificationOperation.CLASSEE_MANUELLE);
                }
                op.setDateModification(LocalDateTime.now());
            }
            return preparerVerrouille(c, op, piece.id(), candidat.montantRapproche(),
                    candidat.commercantReconnu() && !candidat.dateAtypique() ? 90 : 60,
                    "CARTE-OP-" + op.getId() + "-FA-" + piece.id(), "CARTE_ENTREPRISE");
        });
        if (!tx(() -> trouver(c, id).getStatut() == StatutRapprochement.VALIDE)) valider(c, id);
        return id;
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

    private TypePieceRapprochement typePiece(String mode, FactureOuverteRapprochementDto facture) {
        if (ROLE_REMBOURSEMENT.equals(mode)) return TypePieceRapprochement.REMBOURSEMENT_BENEFICIAIRE;
        if (ROLE_CARTE.equals(mode)) return TypePieceRapprochement.DEPENSE_CARTE_ENTREPRISE;
        if (ROLE_ACHAT.equals(mode) && "DEPENSE_ENTREPRISE".equals(facture.typePiece())) return TypePieceRapprochement.DEPENSE_ENTREPRISE;
        return TypePieceRapprochement.FACTURE_ACHAT;
    }

    public void valider(ContexteRequete c, Long id) {
        // Commit de l'intention AVANT le HTTP : une réponse perdue reste rejouable avec le même identifiant.
        tx(() -> {
            var r = trouver(c, id); var op = verrouiller(c, r.getOperationBancaire().getId()); em.refresh(r);
            verifierProposition(r); verifierOperation(c, op, r.getModeRapprochement()); verifierDisponible(op, r.getMontantRapproche(), id);
            if (rapprochements.existsByOperationBancaireIdAndFactureIdAndStatutIn(op.getId(), r.getFactureId(), List.of(StatutRapprochement.VALIDE)))
                throw erreur("RAPPROCHEMENT_DEJA_VALIDE", "Ce couple opération–facture est déjà validé.");
            if (!r.isPaiementEngage()) {
                String mode = r.getModeRapprochement();
                var facture = candidates(c, op, mode).stream().filter(f -> r.getFactureId().equals(f.id())
                        && (mode == null || !mode.startsWith("ROLE_")
                            || Objects.equals(op.getTiersId(), f.tiersId()) && r.getTypePiece() == typePiece(mode, f))).findFirst()
                        .orElseThrow(() -> erreur("FACTURE_NON_CANDIDATE", "La facture n'est plus validée ou éligible."));
                if (r.getMontantRapproche().compareTo(facture.resteAPayer()) > 0)
                    throw erreur("MONTANT_SUPERIEUR_RESTE_FACTURE", "Le montant dépasse le reste à payer.");
            }
            r.setPaiementEngage(true); return null;
        });
        tx(() -> {
            var r = trouver(c, id); var op = verrouiller(c, r.getOperationBancaire().getId()); em.refresh(r);
            verifierProposition(r); verifierOperation(c, op, r.getModeRapprochement()); verifierDisponible(op, r.getMontantRapproche(), id);
            factures.enregistrerPaiement(c, TypeFactureRapprochement.ACHAT, r.getFactureId(),
                    new EnregistrementPaiementFactureDto(r.getMontantRapproche(), op.getDateOperation(),
                            r.getLibelleRapprochement(), op.getId(), r.getId(), op.getTiersId(),
                            r.getModeRapprochement()!=null && r.getModeRapprochement().startsWith("ROLE_")
                                ? r.getTypePiece()==TypePieceRapprochement.REMBOURSEMENT_BENEFICIAIRE
                                : remboursement(op, r.getModeRapprochement())));
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
