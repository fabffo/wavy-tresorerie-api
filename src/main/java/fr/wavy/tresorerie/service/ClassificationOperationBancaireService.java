package fr.wavy.tresorerie.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Service;

import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;

@Service
public class ClassificationOperationBancaireService {

    public void classerAutomatiquement(OperationBancaire operation) {
        classerAutomatiquement(operation, List.of());
    }

    public void classerAutomatiquement(OperationBancaire operation, List<RegleClassificationOperation> regles) {
        TiersReconnu tiers = reconnaitreTiers(operation.getLibelle(), regles);
        operation.setTiersReconnu(tiers.nom());
        operation.setTypeTiersReconnu(tiers.type());
        operation.setTiersId(tiers.tiersId());

        NatureOperationBancaire nature = determinerNature(operation);
        operation.setNatureOperation(nature);
        operation.setStatutClassification(nature == NatureOperationBancaire.AUTRE
                ? StatutClassificationOperation.A_VERIFIER
                : StatutClassificationOperation.CLASSEE_AUTO);
    }

    public NatureOperationBancaire determinerNature(OperationBancaire operation) {
        String texte = normaliser(operation.getLibelle());
        SensOperation sens = operation.getSens();
        TypeTiersReconnu typeTiers = operation.getTypeTiersReconnu();

        if (texte.contains("TVA")) return NatureOperationBancaire.TVA;
        if (texte.contains("URSSAF")) return NatureOperationBancaire.URSSAF;
        if (contientUn(texte, "IMPOT", "DGFIP", " IS ", "PAS", "IMPOT SALAIRES")) return NatureOperationBancaire.IMPOT;
        if (contientMotCleSalaire(texte) && sens == SensOperation.DEBIT) return NatureOperationBancaire.SALAIRE;
        if (contientUn(texte, "NOTE FRAIS", "NDF") && sens == SensOperation.DEBIT) return NatureOperationBancaire.NOTE_DE_FRAIS;
        if (typeTiers == TypeTiersReconnu.SALARIE && sens == SensOperation.DEBIT) return NatureOperationBancaire.SALAIRE;
        if (contientUn(texte, "FRAIS", "COMMISSION", "COTISATION BANCAIRE")) return NatureOperationBancaire.FRAIS_BANCAIRES;
        if (contientUn(texte, "CAPITAL", "APPORT")) return NatureOperationBancaire.APPORT_CAPITAL;
        if (texte.contains("COMPTE COURANT")) return NatureOperationBancaire.COMPTE_COURANT;
        if (texte.contains("VIREMENT INTERNE")) return NatureOperationBancaire.VIREMENT_INTERNE;
        if ((typeTiers == TypeTiersReconnu.FOURNISSEUR || typeTiers == TypeTiersReconnu.FOURNISSEUR_SERVICE)
                && sens == SensOperation.DEBIT) return NatureOperationBancaire.FACTURE_FOURNISSEUR;
        if (typeTiers == TypeTiersReconnu.CLIENT && sens == SensOperation.CREDIT) return NatureOperationBancaire.FACTURE_CLIENT;
        return NatureOperationBancaire.AUTRE;
    }

    public ResultatReconnaissanceTiers reconnaitreTiersDepuisRegles(String libelle, List<RegleClassificationOperation> regles) {
        String texte = normaliser(libelle);
        if (texte.isBlank() || regles == null || regles.isEmpty()) {
            return ResultatReconnaissanceTiers.nonTrouve();
        }
        List<CandidatReconnaissanceTiers> candidats = regles.stream()
                .filter(RegleClassificationOperation::active)
                .filter(regle -> correspond(texte, regle))
                .map(regle -> new CandidatReconnaissanceTiers(
                        regle,
                        regle.priorite(),
                        normaliserFragment(regle.motif()).length()
                ))
                .sorted(Comparator
                        .comparing(CandidatReconnaissanceTiers::priorite).reversed()
                        .thenComparing(Comparator.comparing(CandidatReconnaissanceTiers::longueurMotif).reversed()))
                .toList();
        if (candidats.isEmpty()) {
            return ResultatReconnaissanceTiers.nonTrouve();
        }

        CandidatReconnaissanceTiers meilleur = candidats.get(0);
        boolean ambigu = candidats.stream()
                .skip(1)
                .anyMatch(candidat -> candidat.priorite() == meilleur.priorite()
                        && candidat.longueurMotif() == meilleur.longueurMotif());
        if (ambigu) {
            return ResultatReconnaissanceTiers.ambigu();
        }
        RegleClassificationOperation regle = meilleur.regle();
        return ResultatReconnaissanceTiers.trouve(regle.tiersId(), regle.nomTiers(), regle.typeTiers());
    }

    private TiersReconnu reconnaitreTiers(String libelle, List<RegleClassificationOperation> regles) {
        String texte = normaliser(libelle);
        List<RegleClassificationOperation> reglesTriees = regles == null ? List.of() : regles.stream()
                .filter(RegleClassificationOperation::active)
                .sorted(Comparator.comparing(RegleClassificationOperation::priorite).reversed())
                .toList();
        if (contientMotCleSalaire(texte)) {
            Optional<TiersReconnu> salarie = reconnaitreSalarie(texte, reglesTriees);
            if (salarie.isPresent()) {
                return salarie.get();
            }
            return new TiersReconnu(null, null, null);
        }
        for (RegleClassificationOperation regle : reglesTriees) {
            if (correspond(texte, regle)) {
                return new TiersReconnu(regle.nomTiers(), regle.typeTiers(), regle.tiersId());
            }
        }
        if (texte.contains("LINKJOB")) return new TiersReconnu("LINKJOB", TypeTiersReconnu.FOURNISSEUR_SERVICE, null);
        if (texte.contains("MARIF")) return new TiersReconnu("MARIF", TypeTiersReconnu.CLIENT, null);
        if (texte.contains("FABRICE FOUGERY")) return new TiersReconnu("FABRICE FOUGERY", TypeTiersReconnu.SALARIE, null);
        return new TiersReconnu(null, null, null);
    }

    private Optional<TiersReconnu> reconnaitreSalarie(String texteNormalise, List<RegleClassificationOperation> regles) {
        List<RegleClassificationOperation> salaries = regles.stream()
                .filter(regle -> regle.typeTiers() == TypeTiersReconnu.SALARIE)
                .toList();
        Optional<TiersReconnu> nomPrenom = chercherSalarieParNomComplet(texteNormalise, salaries, true);
        if (nomPrenom.isPresent()) {
            return nomPrenom;
        }
        Optional<TiersReconnu> prenomNom = chercherSalarieParNomComplet(texteNormalise, salaries, false);
        if (prenomNom.isPresent()) {
            return prenomNom;
        }
        Optional<TiersReconnu> nomUnique = chercherSalarieParNomUnique(texteNormalise, salaries);
        if (nomUnique.isPresent()) {
            return nomUnique;
        }
        if (texteNormalise.contains("FABRICE FOUGERY")) {
            return Optional.of(new TiersReconnu("FABRICE FOUGERY", TypeTiersReconnu.SALARIE, null));
        }
        return Optional.empty();
    }

    private Optional<TiersReconnu> chercherSalarieParNomComplet(
            String texteNormalise,
            List<RegleClassificationOperation> salaries,
            boolean nomPuisPrenom
    ) {
        return salaries.stream()
                .filter(regle -> {
                    String nom = normaliserFragment(regle.nom());
                    String prenom = normaliserFragment(regle.prenom());
                    if (nom.isBlank() || prenom.isBlank()) {
                        return false;
                    }
                    String motif = nomPuisPrenom ? nom + " " + prenom : prenom + " " + nom;
                    return texteNormalise.contains(" " + motif + " ");
                })
                .findFirst()
                .map(this::tiersReconnu);
    }

    private Optional<TiersReconnu> chercherSalarieParNomUnique(String texteNormalise, List<RegleClassificationOperation> salaries) {
        Map<String, Long> occurrencesParNom = salaries.stream()
                .map(regle -> normaliserFragment(regle.nom()))
                .filter(nom -> !nom.isBlank())
                .collect(Collectors.groupingBy(nom -> nom, Collectors.counting()));
        return salaries.stream()
                .filter(regle -> {
                    String nom = normaliserFragment(regle.nom());
                    return !nom.isBlank()
                            && occurrencesParNom.getOrDefault(nom, 0L) == 1L
                            && texteNormalise.contains(" " + nom + " ");
                })
                .findFirst()
                .map(this::tiersReconnu);
    }

    private TiersReconnu tiersReconnu(RegleClassificationOperation regle) {
        return new TiersReconnu(regle.nomTiers(), regle.typeTiers(), regle.tiersId());
    }

    private boolean correspond(String texteNormalise, RegleClassificationOperation regle) {
        String motif = normaliser(regle.motif());
        if (texteNormalise.isBlank() || motif.isBlank()) {
            return false;
        }
        return switch (regle.typeRegle()) {
            case "EGAL" -> texteNormalise.equals(motif);
            case "CONTIENT" -> texteNormalise.contains(motif);
            case "COMMENCE_PAR" -> texteNormalise.startsWith(motif);
            case "FINIT_PAR" -> texteNormalise.endsWith(motif);
            case "REGEX" -> correspondRegex(texteNormalise, motif);
            default -> texteNormalise.contains(motif);
        };
    }

    private boolean correspondRegex(String texteNormalise, String motifNormalise) {
        try {
            return Pattern.compile(motifNormalise).matcher(texteNormalise).find();
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private boolean contientUn(String texte, String... motifs) {
        for (String motif : motifs) {
            if (texte.contains(motif)) {
                return true;
            }
        }
        return false;
    }

    private boolean contientMotCleSalaire(String texte) {
        return contientUn(texte, "SALAIRE", "PAIE", "PAYE", "BULLETIN", "REMUNERATION");
    }

    private String normaliserFragment(String valeur) {
        return normaliser(valeur).trim();
    }

    private String normaliser(String valeur) {
        if (valeur == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return (" " + sansAccent.toUpperCase(Locale.ROOT)
                .replaceAll("\\p{C}", " ")
                .replaceAll("[\\s\\u00a0\\u202f]+", " ")
                .trim() + " ");
    }

    public record RegleClassificationOperation(
            Long tiersId,
            String nomTiers,
            TypeTiersReconnu typeTiers,
            String motif,
            String typeRegle,
            int priorite,
            boolean active,
            String nom,
            String prenom
    ) {
        public RegleClassificationOperation(
                Long tiersId,
                String nomTiers,
                TypeTiersReconnu typeTiers,
                String motif,
                String typeRegle,
                int priorite,
                boolean active
        ) {
            this(tiersId, nomTiers, typeTiers, motif, typeRegle, priorite, active, null, null);
        }
    }

    public record ResultatReconnaissanceTiers(
            Long tiersId,
            String nomTiers,
            TypeTiersReconnu typeTiers,
            boolean trouve,
            boolean ambiguite
    ) {
        public static ResultatReconnaissanceTiers trouve(Long tiersId, String nomTiers, TypeTiersReconnu typeTiers) {
            return new ResultatReconnaissanceTiers(tiersId, nomTiers, typeTiers, true, false);
        }

        public static ResultatReconnaissanceTiers nonTrouve() {
            return new ResultatReconnaissanceTiers(null, null, null, false, false);
        }

        public static ResultatReconnaissanceTiers ambigu() {
            return new ResultatReconnaissanceTiers(null, null, null, false, true);
        }
    }

    private record CandidatReconnaissanceTiers(
            RegleClassificationOperation regle,
            int priorite,
            int longueurMotif
    ) {
    }

    private record TiersReconnu(String nom, TypeTiersReconnu type, Long tiersId) {
    }
}
