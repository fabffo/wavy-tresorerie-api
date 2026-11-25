package fr.wavy.tresorerie.service;

import fr.wavy.tresorerie.dto.PieceCarteRapprochementDto;
import fr.wavy.tresorerie.dto.PropositionCarteDto;
import fr.wavy.tresorerie.modele.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Calcul sans écriture. Les dates atypiques restent visibles et demandent une confirmation distincte. */
public final class RapprochementCarteMatcher {
    private RapprochementCarteMatcher() {}
    private static final BigDecimal CENTIME = new BigDecimal("0.01");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{2})/(\\d{2})(?:/(\\d{4}))?(?!\\d)");
    private static final Set<String> MOTS_GENERIQUES = Set.of("FRANCE", "SARL", "SAS", "SASU", "LTD", "OPERATIONS", "DE", "DU", "DES", "LA", "LE", "LES", "ET", "CARTE", "PAIEMENT");

    public static BigDecimal montant(OperationBancaire op) {
        return (op.getDebit() == null ? op.getMontant() : op.getDebit()).abs();
    }

    public static LocalDate dateAchat(String libelle, LocalDate comptabilisation) {
        if (libelle == null || comptabilisation == null) return null;
        var matcher = DATE.matcher(libelle);
        LocalDate resultat = null;
        while (matcher.find()) {
            LocalDate meilleur = null;
            for (int annee = comptabilisation.getYear() - 1; annee <= comptabilisation.getYear() + 1; annee++) {
                try {
                    LocalDate d = LocalDate.of(matcher.group(3) == null ? annee : Integer.parseInt(matcher.group(3)),
                            Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
                    if (meilleur == null || Math.abs(ChronoUnit.DAYS.between(d, comptabilisation)) < Math.abs(ChronoUnit.DAYS.between(meilleur, comptabilisation))) meilleur = d;
                } catch (java.time.DateTimeException ignored) { /* date invalide : ne pas la substituer */ }
            }
            if (meilleur != null) resultat = meilleur;
        }
        return resultat;
    }

    private static String normaliser(String texte) {
        return Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static boolean commercant(String nom, String libelle) {
        var banque = Arrays.asList(normaliser(libelle).split(" +"));
        return Arrays.stream(normaliser(nom).split(" +"))
                .filter(t -> t.length() >= 2 && !t.matches("\\d+") && !MOTS_GENERIQUES.contains(t))
                .anyMatch(t -> banque.stream().anyMatch(b -> t.equals(b)
                        || (Math.min(t.length(), b.length()) >= 5 && (t.startsWith(b) || b.startsWith(t)))));
    }

    static List<PropositionCarteDto> propositionsFournisseurs(OperationBancaire op,
            List<PieceCarteRapprochementDto> pieces, List<OperationBancaire> operations, List<RapprochementBancaire> traces) {
        var compatibles = new LinkedHashMap<PieceCarteRapprochementDto, PropositionCarteDto>();
        for (var piece : pieces) {
            comparerFournisseurs(op, piece).filter(p -> !reservee(op, piece.id(), traces))
                    .ifPresent(p -> compatibles.put(piece, p));
        }
        return compatibles.entrySet().stream().map(candidat -> {
            var piece = candidat.getKey();
            boolean ambiguite = compatibles.size() > 1
                    || operations.stream().anyMatch(autre -> !Objects.equals(autre.getId(), op.getId())
                            && comparerFournisseurs(autre, piece).isPresent()
                            && !reservee(autre, piece.id(), traces));
            return candidat.getValue().avecConflits(ambiguite, ambiguite);
        }).toList();
    }

    private static boolean reservee(OperationBancaire op, Long factureId, List<RapprochementBancaire> traces) {
        return traces.stream().anyMatch(r -> r.getStatut() != StatutRapprochement.ANNULE
                && (Objects.equals(op.getId(), r.getOperationBancaire().getId())
                    || r.getTypeFacture() == TypeFactureRapprochement.ACHAT && factureId.equals(r.getFactureId())));
    }

    static Optional<PropositionCarteDto> comparerFournisseurs(OperationBancaire op, PieceCarteRapprochementDto piece) {
        var date = piece.dateDepenseFiable() != null ? piece.dateDepenseFiable() : piece.datePiece();
        if (!op.isActif() || op.isRapprochee() || op.getSens() != SensOperation.DEBIT
                || op.getTypeOperation() != TypeOperationBancaire.PAIEMENT_CB
                || op.getStatutRapprochement() != StatutOperationBancaire.NON_RAPPROCHEE
                || TresorerieService.mouvementFinancierIncompatible(op)
                || !Objects.equals(op.getTenantId(), piece.tenantId())
                || !Objects.equals(op.getSocieteInterneId(), piece.societeInterneId())
                || op.getTiersId() == null || !op.getTiersId().equals(piece.tiersId())
                || date == null || op.getDateOperation() == null || op.getDateOperation().isBefore(date)
                || op.getDateOperation().isAfter(date.plusDays(3))
                || piece.montantTtc() == null || piece.montant() == null || piece.montantTtc().signum() <= 0
                || piece.montantTtc().compareTo(montant(op)) != 0 || piece.montant().compareTo(piece.montantTtc()) != 0) {
            return Optional.empty();
        }
        return Optional.of(new PropositionCarteDto(op.getId(), piece.id(), piece.tiersId(), piece.numeroPiece(),
                op.getLibelle(), piece.nomTiers(), op.getDateOperation(), null, date, montant(op),
                piece.montantTtc(), piece.montantTtc(), BigDecimal.ZERO, false, true, false, false, false,
                List.of("Tiers identifié et montant TTC exact", "Date bancaire comprise entre la date de référence et J+3")));
    }

    public static Optional<PropositionCarteDto> comparer(OperationBancaire op, PieceCarteRapprochementDto piece) {
        if (!op.isActif() || op.isRapprochee() || op.getSens() != SensOperation.DEBIT
                || op.getStatutRapprochement() != StatutOperationBancaire.NON_RAPPROCHEE
                || !Objects.equals(op.getTenantId(), piece.tenantId())
                || !Objects.equals(op.getSocieteInterneId(), piece.societeInterneId())
                || piece.tiersId() == null || piece.montant() == null || piece.montant().signum() <= 0
                || op.getDateOperation() == null || montant(op).signum() <= 0) return Optional.empty();
        BigDecimal ecart = montant(op).subtract(piece.montant()).abs();
        if (ecart.compareTo(CENTIME) > 0) return Optional.empty();
        LocalDate achat = dateAchat(op.getLibelle(), op.getDateOperation());
        LocalDate dateComparaison = achat == null ? op.getDateOperation() : achat;
        boolean atypique = piece.dateDepense() == null
                || Math.abs(ChronoUnit.DAYS.between(piece.dateDepense(), dateComparaison)) > 7
                || Math.abs(ChronoUnit.DAYS.between(dateComparaison, op.getDateOperation())) > 7;
        boolean marchand = commercant(piece.nomTiers(), op.getLibelle()) || commercant(piece.nomMarchand(), op.getLibelle());
        boolean conflit = op.getTiersId() != null && !op.getTiersId().equals(piece.tiersId());
        List<String> criteres = new ArrayList<>();
        criteres.add(ecart.signum() == 0 ? "Montant exact" : "Écart de 0,01 : le reliquat sera conservé");
        criteres.add(atypique ? "Date atypique ou absente : vérification du justificatif requise" : "Dates proches (7 jours maximum)");
        criteres.add(achat == null ? "Date bancaire utilisée, date d'achat absente du libellé" : "Date d'achat extraite du libellé bancaire");
        criteres.add(marchand ? "Indice de commerçant concordant" : "Nom de commerçant différent : vérifier l'enseigne ou son alias");
        if (conflit) criteres.add("Conflit : le tiers bancaire diffère de celui de la pièce");
        return Optional.of(new PropositionCarteDto(op.getId(), piece.id(), piece.tiersId(), piece.numeroPiece(),
                op.getLibelle(), piece.nomTiers(), op.getDateOperation(), achat, piece.dateDepense(),
                montant(op), piece.montant(), montant(op).min(piece.montant()), ecart,
                atypique, marchand, op.getTiersId() == null || op.getNatureOperation() != NatureOperationBancaire.FACTURE_FOURNISSEUR
                        || (op.getTypeTiersReconnu() != TypeTiersReconnu.FOURNISSEUR && op.getTypeTiersReconnu() != TypeTiersReconnu.FOURNISSEUR_SERVICE),
                false, conflit, List.copyOf(criteres)));
    }
}
