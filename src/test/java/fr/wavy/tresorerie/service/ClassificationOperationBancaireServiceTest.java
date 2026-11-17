package fr.wavy.tresorerie.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import fr.wavy.tresorerie.modele.NatureOperationBancaire;
import fr.wavy.tresorerie.modele.OperationBancaire;
import fr.wavy.tresorerie.modele.SensOperation;
import fr.wavy.tresorerie.modele.StatutClassificationOperation;
import fr.wavy.tresorerie.modele.TypeTiersReconnu;
import fr.wavy.tresorerie.service.ClassificationOperationBancaireService.RegleClassificationOperation;

import java.util.List;

class ClassificationOperationBancaireServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PAIEMENT PAR CARTE INDIGO", "PAIEMENT FOURNISSEUR", "PAIEMENTS CB", "PREPAIEMENT"})
    void paiementNeDeclencheJamaisLaReconnaissanceSalaire(String libelle) {
        var operation = new OperationBancaire(); operation.setLibelle(libelle); operation.setSens(SensOperation.DEBIT);
        new ClassificationOperationBancaireService().classerAutomatiquement(operation, List.of(
                new RegleClassificationOperation(7L, "INDIGO", TypeTiersReconnu.FOURNISSEUR, "INDIGO", "CONTIENT", 100, true)));
        assertThat(operation.getNatureOperation()).isNotEqualTo(NatureOperationBancaire.SALAIRE);
        if (libelle.contains("INDIGO")) assertThat(operation.getTiersId()).isEqualTo(7L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"VIR PAIE JANVIER", "VIREMENT PAYE", "SALAIRE JANVIER", "BULLETIN DE PAIE", "REMUNERATION", "PAIES"})
    void conserveLesVeritablesLibellesDePaie(String libelle) {
        var operation = new OperationBancaire(); operation.setLibelle(libelle); operation.setSens(SensOperation.DEBIT);
        new ClassificationOperationBancaireService().classerAutomatiquement(operation);
        assertThat(operation.getNatureOperation()).isEqualTo(NatureOperationBancaire.SALAIRE);
    }
    private final ClassificationOperationBancaireService service = new ClassificationOperationBancaireService();

    @Test
    void remboursementSalarieNeDevientPasUnSalaire() {
        OperationBancaire op = operation("REMBOURSEMENT NOTE DE FRAIS SALARIE", SensOperation.DEBIT);
        op.setTypeTiersReconnu(TypeTiersReconnu.SALARIE);
        assertThat(service.determinerNature(op)).isEqualTo(NatureOperationBancaire.NOTE_DE_FRAIS);
    }

    @Test
    void classeLesOperationsBancairesSelonLesReglesPrioritaires() {
        assertClassification("VIR LINKJOB FACTURE", SensOperation.DEBIT, NatureOperationBancaire.FACTURE_FOURNISSEUR, "LINKJOB", TypeTiersReconnu.FOURNISSEUR_SERVICE);
        assertClassification("VIR MARIF REGLEMENT", SensOperation.CREDIT, NatureOperationBancaire.FACTURE_CLIENT, "MARIF", TypeTiersReconnu.CLIENT);
        assertClassification("FABRICE FOUGERY SALAIRE", SensOperation.DEBIT, NatureOperationBancaire.SALAIRE, "FABRICE FOUGERY", TypeTiersReconnu.SALARIE);
        assertClassification("FABRICE FOUGERY NOTE FRAIS", SensOperation.DEBIT, NatureOperationBancaire.NOTE_DE_FRAIS, "FABRICE FOUGERY", TypeTiersReconnu.SALARIE);
        assertClassification("VIR DGFIP TVA MENSUELLE", SensOperation.DEBIT, NatureOperationBancaire.TVA, null, null);
        assertClassification("URSSAF", SensOperation.DEBIT, NatureOperationBancaire.URSSAF, null, null);
        assertClassification("IMPOT SOCIETE", SensOperation.DEBIT, NatureOperationBancaire.IMPOT, null, null);
        assertClassification("CAPITAL SOCIAL", SensOperation.CREDIT, NatureOperationBancaire.APPORT_CAPITAL, null, null);
    }

    @Test
    void marqueAutreAverifierSiAucuneRegleNeCorrespond() {
        OperationBancaire operation = operation("LIBELLE INCONNU", SensOperation.DEBIT);

        service.classerAutomatiquement(operation);

        assertThat(operation.getNatureOperation()).isEqualTo(NatureOperationBancaire.AUTRE);
        assertThat(operation.getStatutClassification()).isEqualTo(StatutClassificationOperation.A_VERIFIER);
    }

    @Test
    void neReconnaitPasUnFournisseurQuandLeLibelleContientSalaire() {
        OperationBancaire operation = operation("VIR IMPOT SALAIRES", SensOperation.DEBIT);
        List<RegleClassificationOperation> regles = List.of(new RegleClassificationOperation(
                42L,
                "IMPOT SALAIRES",
                TypeTiersReconnu.FOURNISSEUR,
                "IMPOT SALAIRES",
                "CONTIENT",
                100,
                true
        ));

        service.classerAutomatiquement(operation, regles);

        assertThat(operation.getTiersId()).isNull();
        assertThat(operation.getTiersReconnu()).isNull();
        assertThat(operation.getTypeTiersReconnu()).isNull();
        assertThat(operation.getNatureOperation()).isEqualTo(NatureOperationBancaire.IMPOT);
        assertThat(operation.getStatutClassification()).isEqualTo(StatutClassificationOperation.CLASSEE_AUTO);
    }

    @Test
    void reconnaitLesSalariesDuReferentielEnPrioritePourLesLibellesDeSalaire() {
        List<RegleClassificationOperation> regles = List.of(
                salarie(101L, "Lucas Martin", "Martin", "Lucas"),
                salarie(102L, "Adeline Marive", "Marive", "Adeline"),
                salarie(103L, "Fabrice Fougery", "Fougery", "Fabrice"),
                new RegleClassificationOperation(
                        201L,
                        "Lucas Martin Fournisseur",
                        TypeTiersReconnu.FOURNISSEUR,
                        "LUCAS MARTIN",
                        "CONTIENT",
                        2_000,
                        true
                )
        );

        assertClassification(
                "VIR LUCAS MARTIN SALAIRE JANVIER",
                SensOperation.DEBIT,
                NatureOperationBancaire.SALAIRE,
                "Lucas Martin",
                TypeTiersReconnu.SALARIE,
                regles
        );
        assertClassification(
                "VIR ADELINE MARIVE SALAIRE JANVIER",
                SensOperation.DEBIT,
                NatureOperationBancaire.SALAIRE,
                "Adeline Marive",
                TypeTiersReconnu.SALARIE,
                regles
        );
        assertClassification(
                "VIR FABRICE FOUGERY SALAIRE JANVIER",
                SensOperation.DEBIT,
                NatureOperationBancaire.SALAIRE,
                "Fabrice Fougery",
                TypeTiersReconnu.SALARIE,
                regles
        );
    }

    @Test
    void calculeLeTiersReconnuDepuisLesReglesOperationsBancaires() {
        List<RegleClassificationOperation> reglesOperationsBancaires = List.of(
                new RegleClassificationOperation(
                        101L,
                        "Lucas Martin",
                        TypeTiersReconnu.SALARIE,
                        "Martin",
                        "CONTIENT",
                        100,
                        true
                ),
                new RegleClassificationOperation(
                        201L,
                        "Martin Fournisseur",
                        TypeTiersReconnu.FOURNISSEUR,
                        "Martin",
                        "CONTIENT",
                        50,
                        true
                )
        );

        ClassificationOperationBancaireService.ResultatReconnaissanceTiers resultat =
                service.reconnaitreTiersDepuisRegles("VIR LUCAS MARTIN SALAIRE JANVIER", reglesOperationsBancaires);

        assertThat(resultat.trouve()).isTrue();
        assertThat(resultat.tiersId()).isEqualTo(101L);
        assertThat(resultat.nomTiers()).isEqualTo("Lucas Martin");
        assertThat(resultat.typeTiers()).isEqualTo(TypeTiersReconnu.SALARIE);
    }

    private void assertClassification(String libelle, SensOperation sens, NatureOperationBancaire nature, String tiers, TypeTiersReconnu typeTiers) {
        assertClassification(libelle, sens, nature, tiers, typeTiers, List.of());
    }

    private void assertClassification(
            String libelle,
            SensOperation sens,
            NatureOperationBancaire nature,
            String tiers,
            TypeTiersReconnu typeTiers,
            List<RegleClassificationOperation> regles
    ) {
        OperationBancaire operation = operation(libelle, sens);

        service.classerAutomatiquement(operation, regles);

        assertThat(operation.getNatureOperation()).isEqualTo(nature);
        assertThat(operation.getStatutClassification()).isEqualTo(StatutClassificationOperation.CLASSEE_AUTO);
        assertThat(operation.getTiersReconnu()).isEqualTo(tiers);
        assertThat(operation.getTypeTiersReconnu()).isEqualTo(typeTiers);
    }

    private OperationBancaire operation(String libelle, SensOperation sens) {
        OperationBancaire operation = new OperationBancaire();
        operation.setLibelle(libelle);
        operation.setSens(sens);
        return operation;
    }

    private RegleClassificationOperation salarie(Long tiersId, String libelle, String nom, String prenom) {
        return new RegleClassificationOperation(
                tiersId,
                libelle,
                TypeTiersReconnu.SALARIE,
                libelle,
                "CONTIENT",
                1_000,
                true,
                nom,
                prenom
        );
    }
    @Test void identitesBancairesConnuesSansMontantEtPonctuation() {
        var noms=List.of("Adobe Systems Software Ireland Ltd","Microsoft France SAS","OpenAI OpCo LLC","LinkedIn Ireland Unlimited Company","Benome");
        var libelles=List.of("Paiement Adobe.com Saggart 09/01","Paiement MICROSOFT£G134725849 10/01","Paiement OPENAI *CHATGPT SUBS 12/01","Paiement LinkedIn Recruiter 12/01","Virement BENOME BENOIT SIRA");
        for(int i=0;i<noms.size();i++) {
            var tiers=new fr.wavy.tresorerie.dto.TiersTresorerieDto(21L,java.util.Set.of("FOURNISSEUR_GENERAL"),noms.get(i),null,null,noms.get(i),true,false);
            assertThat(service.proposerTiers(operation(libelles.get(i),SensOperation.DEBIT),List.of(),List.of(tiers))).containsExactly(tiers);
        }
    }
    @Test void ambiguiteEtClassificationManuellePreservees() {
        var regles=List.of(new RegleClassificationOperation(1L,"Adobe",TypeTiersReconnu.FOURNISSEUR,"ADOBE","CONTIENT",100,true),
                          new RegleClassificationOperation(2L,"Adobe bis",TypeTiersReconnu.FOURNISSEUR,"ADOBE","CONTIENT",100,true));
        assertThat(service.reconnaitreTiersDepuisRegles("Paiement Adobe.com",regles).ambiguite()).isTrue();
        var op=operation("Paiement Adobe.com",SensOperation.DEBIT);op.setTiersId(99L);op.setNatureOperation(NatureOperationBancaire.COMPTE_COURANT);op.setStatutClassification(StatutClassificationOperation.CLASSEE_MANUELLE);
        service.classerAutomatiquement(op,regles);assertThat(op.getTiersId()).isEqualTo(99L);assertThat(op.getNatureOperation()).isEqualTo(NatureOperationBancaire.COMPTE_COURANT);
    }
    @Test void fraisOpenAINeDesignentPasLeFournisseurMemeAvecRegleBancaire() {
        var t=new fr.wavy.tresorerie.dto.TiersTresorerieDto(30L,java.util.Set.of("FOURNISSEUR_GENERAL"),"OpenAI OpCo LLC",null,null,null,true,false);
        var r=new RegleClassificationOperation(30L,t.libelle(),TypeTiersReconnu.FOURNISSEUR,"OPENAI","CONTIENT",100,true);
        assertThat(service.proposerTiers(operation("Frais carte étranger OPENAI CHATGPT",SensOperation.DEBIT),List.of(r),List.of(t))).isEmpty();
    }

}
