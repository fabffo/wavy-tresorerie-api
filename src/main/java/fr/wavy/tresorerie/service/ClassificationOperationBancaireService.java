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

    private fr.wavy.tresorerie.client.TiersTresorerieClient tiersClient;
    private fr.wavy.tresorerie.client.FacturesTresorerieClient facturesClient;
    public ClassificationOperationBancaireService() {}
    @org.springframework.beans.factory.annotation.Autowired
    public ClassificationOperationBancaireService(fr.wavy.tresorerie.client.TiersTresorerieClient tiers,
            fr.wavy.tresorerie.client.FacturesTresorerieClient factures) { tiersClient=tiers; facturesClient=factures; }

    /** Source bancaire unique ; les règles d'import facture ne sont jamais recopiées. */
    public List<RegleClassificationOperation> chargerRegles(fr.wavy.tresorerie.configuration.ContexteRequete c) {
        var tiers = tiersClient.listerTiersActifs(c);
        var regles = new java.util.ArrayList<RegleClassificationOperation>();
        for (var r : facturesClient.listerReglesRapprochementFournisseur(c)) {
            if (Boolean.FALSE.equals(r.actif()) || (r.typeRegle()!=null && !"OPERATION_BANCAIRE".equals(r.typeRegle()))) continue;
            tiers.stream().filter(t -> t.id().equals(r.tiersId()) && !Boolean.FALSE.equals(t.actif())).findFirst().ifPresent(t ->
                regles.add(new RegleClassificationOperation(t.id(), t.libelle(), typeTiers(t.roles()), r.motif(),
                    r.typeMotif()==null ? "CONTIENT" : r.typeMotif(), r.scorePriorite()==null ? 100 : r.scorePriorite(), true,t.nom(),t.prenom(),t.roles()!=null && t.roles().contains("BANQUE"))));
        }
        for (var t : tiers) if (t.roles()!=null && t.roles().contains("SALARIE") && !Boolean.FALSE.equals(t.actif()))
            regles.add(new RegleClassificationOperation(t.id(),t.libelle(),TypeTiersReconnu.SALARIE,t.libelle(),"CONTIENT",1000,true,t.nom(),t.prenom()));
        return List.copyOf(regles);
    }

    public static TypeTiersReconnu typeTiers(java.util.Set<String> roles) {
        if (roles==null) return TypeTiersReconnu.AUTRE;
        if (roles.contains("SALARIE")) return TypeTiersReconnu.SALARIE;
        if (roles.contains("CLIENT")) return TypeTiersReconnu.CLIENT;
        if (roles.contains("FOURNISSEUR_SERVICES") || roles.contains("PRESTATAIRE")) return TypeTiersReconnu.FOURNISSEUR_SERVICE;
        if (roles.contains("FOURNISSEUR_GENERAL") || roles.contains("FOURNISSEUR") || roles.contains("MARCHAND")) return TypeTiersReconnu.FOURNISSEUR;
        if (roles.contains("ORGANISME_FISCAL") || roles.contains("ORGANISME_SOCIAL")) return TypeTiersReconnu.ADMINISTRATION;
        return TypeTiersReconnu.AUTRE;
    }

    /** Recherche plausible sans écriture et sans comparaison de montants. */
    public List<fr.wavy.tresorerie.dto.TiersTresorerieDto> proposerTiers(OperationBancaire op,
            List<RegleClassificationOperation> regles, List<fr.wavy.tresorerie.dto.TiersTresorerieDto> tiers) {
        boolean frais = fraisBancaires(normaliser(op.getLibelle()));
        var resultat=reconnaitreTiersDepuisRegles(op.getLibelle(),regles);
        if (resultat.trouve()) return tiers.stream().filter(t -> t.id().equals(resultat.tiersId()))
            .filter(t -> !frais || banque(t)).toList();
        if (resultat.ambiguite()) {
            var ids=regles.stream().filter(r -> r.active() && correspond(normaliser(op.getLibelle()),r)).map(RegleClassificationOperation::tiersId).toList();
            return tiers.stream().filter(t -> ids.contains(t.id())).filter(t -> !frais || banque(t)).toList();
        }
        var generiques=java.util.Set.of("FRANCE","IRELAND","SYSTEMS","SOFTWARE","COMPANY","UNLIMITED","LLC","LTD","SAS","SARL","OPCO","SERVICES");
        String banque=normaliser(op.getLibelle());
        return tiers.stream().filter(t -> !Boolean.FALSE.equals(t.actif())).filter(t -> !frais || banque(t)).filter(t -> {
            if (t.roles()!=null && t.roles().contains("SALARIE"))
                return banque.contains(normaliser(t.libelle())) || (t.nom()!=null && t.prenom()!=null && banque.contains(normaliser(t.nom()+" "+t.prenom())));
            return java.util.stream.Stream.of(t.raisonSociale(),t.nomCommercial(),t.enseigne(),t.nomNormalise(),t.nom(),t.prenom(),t.siren(),t.siret())
                .filter(java.util.Objects::nonNull).flatMap(n -> java.util.Arrays.stream(normaliser(n).trim().split(" +")))
                .filter(n -> n.length()>=3 && !generiques.contains(n))
                .anyMatch(n -> banque.contains(" "+n+" "));
        }).toList();
    }

    private boolean fraisBancaires(String texte) {
        return contientUn(texte,"FRAIS","COMMISSION","COTISATION BANCAIRE") && !contientUn(texte,"NOTE FRAIS","NOTE DE FRAIS","NDF","REMBOURSEMENT");
    }

    private static boolean banque(fr.wavy.tresorerie.dto.TiersTresorerieDto t) {
        return (t.roles()!=null && t.roles().contains("BANQUE")) || normaliser(t.libelle()).contains(" BANQUE ");
    }

    public void classerAutomatiquement(OperationBancaire operation) {
        classerAutomatiquement(operation, List.of());
    }

    public void classerAutomatiquement(OperationBancaire operation, List<RegleClassificationOperation> regles) {
        if (operation.getStatutClassification()==StatutClassificationOperation.CLASSEE_MANUELLE) return;
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
        if (contientUn(texte, "NOTE FRAIS", "NOTE DE FRAIS", "NDF", "REMBOURSEMENT") && sens == SensOperation.DEBIT)
            return NatureOperationBancaire.NOTE_DE_FRAIS;
        if (contientMotCleSalaire(texte) && sens == SensOperation.DEBIT) return NatureOperationBancaire.SALAIRE;
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
        boolean fiscal=contientUn(texte,"IMPOT","DGFIP","URSSAF","TVA");
        boolean salaire=contientMotCleSalaire(texte) && !fiscal;
        boolean frais=fraisBancaires(texte);
        List<CandidatReconnaissanceTiers> candidats = regles.stream()
                .filter(RegleClassificationOperation::active)
                .filter(r -> !fiscal || r.typeTiers()==TypeTiersReconnu.ADMINISTRATION)
                .filter(r -> !salaire || r.typeTiers()==TypeTiersReconnu.SALARIE)
                .filter(r -> !frais || r.banque())
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
            if (salaire) {
                var salaries=regles.stream().filter(RegleClassificationOperation::active).filter(r -> r.typeTiers()==TypeTiersReconnu.SALARIE).toList();
                var complets=salaries.stream().filter(r -> r.nom()!=null && r.prenom()!=null &&
                    (texte.contains(normaliser(r.nom()+" "+r.prenom())) || texte.contains(normaliser(r.prenom()+" "+r.nom())))).toList();
                if (complets.stream().map(RegleClassificationOperation::tiersId).distinct().count()>1) return ResultatReconnaissanceTiers.ambigu();
                var reconnu=reconnaitreSalarie(texte,salaries);
                if (reconnu.isPresent() && reconnu.get().tiersId()!=null) return ResultatReconnaissanceTiers.trouve(reconnu.get().tiersId(),reconnu.get().nom(),reconnu.get().type());
            }
            return ResultatReconnaissanceTiers.nonTrouve();
        }

        CandidatReconnaissanceTiers meilleur = candidats.get(0);
        boolean ambigu = candidats.stream()
                .skip(1)
                .anyMatch(candidat -> candidat.priorite() == meilleur.priorite()
                        && candidat.longueurMotif() == meilleur.longueurMotif()
                        && !java.util.Objects.equals(candidat.regle().tiersId(), meilleur.regle().tiersId()));
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
        var reconnu=reconnaitreTiersDepuisRegles(libelle,reglesTriees);
        if (reconnu.trouve()) return new TiersReconnu(reconnu.nomTiers(),reconnu.typeTiers(),reconnu.tiersId());
        if (reconnu.ambiguite()) return new TiersReconnu(null,null,null);
        if (contientMotCleSalaire(texte)) return reconnaitreSalarie(texte,reglesTriees).orElse(new TiersReconnu(null,null,null));
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
        return Pattern.compile("(?U)\\b(?:SALAIRE[S]?|PAIE[S]?|PAYE[S]?|BULLETIN[S]?|REMUNERATION[S]?)\\b")
                .matcher(texte).find();
    }

    private String normaliserFragment(String valeur) {
        return normaliser(valeur).trim();
    }

    private static String normaliser(String valeur) {
        if (valeur == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return (" " + sansAccent.toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
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
            String prenom,
            boolean banque
    ) {
        public RegleClassificationOperation(Long tiersId,String nomTiers,TypeTiersReconnu typeTiers,String motif,
                String typeRegle,int priorite,boolean active,String nom,String prenom) {
            this(tiersId,nomTiers,typeTiers,motif,typeRegle,priorite,active,nom,prenom,normaliser(nomTiers).contains(" BANQUE "));
        }
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
