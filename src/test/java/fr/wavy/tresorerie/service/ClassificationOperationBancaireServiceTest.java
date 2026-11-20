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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"CLIENT","MARCHAND","FOURNISSEUR_GENERAL","FOURNISSEUR_SERVICES","PRESTATAIRE","SALARIE","PORTEUR_CARTE","BANQUE","ORGANISME_SOCIAL","ORGANISME_FISCAL","CONTACT","CANDIDAT","AUTRE"})
    void regleBancaireChargeePourToutRoleActif(String role) {
        var tiers=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.TiersTresorerieClient.class);
        var factures=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.FacturesTresorerieClient.class);
        var contexte=new fr.wavy.tresorerie.configuration.ContexteRequete(1L,2L,3L,List.of(),null);
        var t=new fr.wavy.tresorerie.dto.TiersTresorerieDto(21L,java.util.Set.of(role),"Tiers synthétique",null,null,null,true,false);
        org.mockito.Mockito.when(tiers.listerTiersActifs(contexte)).thenReturn(List.of(t));
        org.mockito.Mockito.when(factures.listerReglesRapprochementFournisseur(contexte)).thenReturn(List.of(
            new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(8L,1L,3L,21L,"Alias MEROVE","MEROVE","OPERATION_BANCAIRE","CONTIENT",100,true)));
        var classement=new ClassificationOperationBancaireService(tiers,factures);
        var regles=classement.chargerRegles(contexte);
        assertThat(classement.reconnaitreTiersDepuisRegles("VIR SEPA MEROVE",regles).tiersId()).isEqualTo(21L);
        assertThat(classement.proposerTiers(operation("VIR SEPA MEROVE",SensOperation.DEBIT),regles,List.of(t))).containsExactly(t);
        if (role.equals("SALARIE")) {
            var op=operation("VIR SEPA MEROVE",SensOperation.DEBIT);classement.classerAutomatiquement(op,regles);
            assertThat(op.getTypeTiersReconnu()).isEqualTo(TypeTiersReconnu.SALARIE);
            assertThat(op.getNatureOperation()).isEqualTo(NatureOperationBancaire.SALAIRE);
        }
        org.mockito.Mockito.verify(tiers).listerTiersActifs(contexte);
        org.mockito.Mockito.verify(factures).listerReglesRapprochementFournisseur(contexte);
    }
    @Test void ignoreImportReglesDesactiveesEtTiersInactifs() {
        var tiers=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.TiersTresorerieClient.class);
        var factures=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.FacturesTresorerieClient.class);
        var contexte=new fr.wavy.tresorerie.configuration.ContexteRequete(1L,2L,3L,List.of(),null);
        org.mockito.Mockito.when(tiers.listerTiersActifs(contexte)).thenReturn(List.of(
            new fr.wavy.tresorerie.dto.TiersTresorerieDto(21L,java.util.Set.of("CLIENT"),"Actif",null,null,null,true,false),
            new fr.wavy.tresorerie.dto.TiersTresorerieDto(22L,java.util.Set.of("CLIENT"),"Inactif",null,null,null,false,false)));
        org.mockito.Mockito.when(factures.listerReglesRapprochementFournisseur(contexte)).thenReturn(List.of(
            new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(1L,1L,3L,21L,"Import","ADOBE","IMPORT_FACTURE","CONTIENT",100,true),
            new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(2L,1L,3L,21L,"Inactive","MEROVE","OPERATION_BANCAIRE","CONTIENT",100,false),
            new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(3L,1L,3L,22L,"Tiers inactif","MEROVE","OPERATION_BANCAIRE","CONTIENT",100,true)));
        assertThat(new ClassificationOperationBancaireService(tiers,factures).chargerRegles(contexte)).isEmpty();
    }
    @Test void banqueReconnaissableParRoleSansMotBanqueDansSonNom() {
        var banque=new fr.wavy.tresorerie.dto.TiersTresorerieDto(1L,java.util.Set.of("BANQUE"),"Crédit Mutuel",null,null,null,true,false);
        var marchand=new fr.wavy.tresorerie.dto.TiersTresorerieDto(2L,java.util.Set.of("MARCHAND"),"OpenAI",null,null,null,true,false);
        var regles=List.of(new RegleClassificationOperation(1L,"Crédit Mutuel",TypeTiersReconnu.AUTRE,"FRAIS","CONTIENT",100,true,null,null,true),
                          new RegleClassificationOperation(2L,"OpenAI",TypeTiersReconnu.FOURNISSEUR,"OPENAI","CONTIENT",100,true));
        var op=operation("Frais carte étranger OPENAI",SensOperation.DEBIT);
        assertThat(service.reconnaitreTiersDepuisRegles(op.getLibelle(),regles).tiersId()).isEqualTo(1L);
        assertThat(service.proposerTiers(op,regles,List.of(banque,marchand))).containsExactly(banque);
        assertThat(service.proposerTiers(operation("Commission Crédit Mutuel",SensOperation.DEBIT),List.of(),List.of(banque,marchand))).containsExactly(banque);
    }

    @Test void propositionsConserventLesReglesDePrioritesDifferentesEtLesIdentitesPlausibles() {
        var a=new fr.wavy.tresorerie.dto.TiersTresorerieDto(21L,java.util.Set.of("AUTRE"),"Organisation A",null,null,null,true,false);
        var b=new fr.wavy.tresorerie.dto.TiersTresorerieDto(22L,java.util.Set.of("AUTRE"),"Organisation B",null,null,null,true,false);
        var c=new fr.wavy.tresorerie.dto.TiersTresorerieDto(23L,java.util.Set.of("MARCHAND"),"Adobe France",null,null,null,true,false);
        var regles=List.of(new RegleClassificationOperation(21L,"Organisation A",TypeTiersReconnu.AUTRE,"ADOBE","CONTIENT",200,true),
                new RegleClassificationOperation(22L,"Organisation B",TypeTiersReconnu.AUTRE,"ADOBE.COM","CONTIENT",100,true));
        var op=operation("PAIEMENT ADOBE.COM SAGGART",SensOperation.DEBIT);
        assertThat(service.proposerTiers(op,regles,List.of(a,b,c))).containsExactly(a,b,c);
        // La reconnaissance automatique conserve son classement existant, sans écriture lors des propositions.
        assertThat(service.reconnaitreTiersDepuisRegles(op.getLibelle(),regles).tiersId()).isEqualTo(21L);
        assertThat(op.getTiersId()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "ELIOS DIGITAL SASU,VIR INST vers ELIOS DIGITAL SASU nouvelle reference,FOURNISSEUR_SERVICE",
        "ADOBE.COM,PAIEMENT ADOBE.COM SAGGART 30/12,FOURNISSEUR",
        "ALICE MEROVE,VIR SEPA ALICE MEROVE nouvelle reference,SALARIE"
    })
    void regleConfirmeeReconnaissableLorsDunImportUlterieur(String motif,String libelle,String type) {
        var regle=new RegleClassificationOperation(9L,motif,TypeTiersReconnu.valueOf(type),motif,"CONTIENT",100,true);
        var importee=operation(libelle,SensOperation.DEBIT);
        service.classerAutomatiquement(importee,List.of(regle));
        assertThat(importee.getTiersId()).isEqualTo(9L);
        assertThat(importee.getTypeTiersReconnu()).isEqualTo(TypeTiersReconnu.valueOf(type));
        assertThat(importee.isRapprochee()).isFalse();
    }

    @Test void benomeEnregistrePrimeSurLIdentiteImpliciteDeBenoitSiraLorsDunImport() {
        var tiers=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.TiersTresorerieClient.class);
        var factures=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.FacturesTresorerieClient.class);
        var c=new fr.wavy.tresorerie.configuration.ContexteRequete(1L,2L,3L,List.of(),null);
        var personnes=List.of(new fr.wavy.tresorerie.dto.TiersTresorerieDto(7L,java.util.Set.of("PRESTATAIRE"),"Benome",null,null,null,true,false),
                new fr.wavy.tresorerie.dto.TiersTresorerieDto(59L,java.util.Set.of("SALARIE"),null,"Sira","Benoit",null,true,false));
        org.mockito.Mockito.when(tiers.listerTiersActifs(c)).thenReturn(personnes);
        org.mockito.Mockito.when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(1L,1L,3L,7L,"Benome","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true)));
        var moteur=new ClassificationOperationBancaireService(tiers,factures);
        var bancaire=moteur.chargerReglesBancaires(c,personnes);
        String libelle="Virement émis - VIR INST vers Benome Benoit Sira - wavy services Fac83 nov25 B.Sira";
        assertThat(moteur.reconnaitreRegleUnique(libelle,bancaire).tiersId()).isEqualTo(7L);
        assertThat(moteur.proposerTiers(operation(libelle,SensOperation.DEBIT),bancaire,personnes)).hasSize(2);
        var nouvelle=operation(libelle,SensOperation.DEBIT);moteur.classerAutomatiquement(nouvelle,moteur.chargerRegles(c));
        assertThat(nouvelle.getTiersId()).isEqualTo(7L);assertThat(nouvelle.isRapprochee()).isFalse();
    }
    @Test void deuxReglesBancairesVersDeuxTiersRestentAmbiguesMalgreLesPriorites() {
        var a=new RegleClassificationOperation(7L,"Benome",TypeTiersReconnu.FOURNISSEUR_SERVICE,"BENOME","CONTIENT",100,true,null,null,false,true);
        var b=new RegleClassificationOperation(59L,"Benoit Sira",TypeTiersReconnu.SALARIE,"SIRA","CONTIENT",1000,true,null,null,false,true);
        assertThat(service.reconnaitreRegleUnique("VIR Benome Benoit Sira",List.of(a,b)).ambiguite()).isTrue();
        var nouvelle=operation("VIR Benome Benoit Sira",SensOperation.DEBIT);service.classerAutomatiquement(nouvelle,List.of(a,b));
        assertThat(nouvelle.getTiersId()).isNull();
        assertThat(service.reconnaitreRegleUnique("VIR Benome",List.of(a,new RegleClassificationOperation(7L,"Benome",TypeTiersReconnu.FOURNISSEUR_SERVICE,"VIR BENOME","CONTIENT",20,true))).tiersId()).isEqualTo(7L);
    }
    @Test void reglesBancairesActivesDuSeulTenantEtDeLaSeuleSociete() {
        var tiers=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.TiersTresorerieClient.class);
        var factures=org.mockito.Mockito.mock(fr.wavy.tresorerie.client.FacturesTresorerieClient.class);
        var c=new fr.wavy.tresorerie.configuration.ContexteRequete(1L,2L,3L,List.of(),null);
        var personnes=List.of(new fr.wavy.tresorerie.dto.TiersTresorerieDto(7L,java.util.Set.of("PRESTATAIRE"),"Benome",null,null,null,true,false),
                new fr.wavy.tresorerie.dto.TiersTresorerieDto(8L,java.util.Set.of("PRESTATAIRE"),"Inactif",null,null,null,false,false));
        org.mockito.Mockito.when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(1L,1L,3L,7L,"A","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true),
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(2L,2L,3L,7L,"B","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true),
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(3L,1L,4L,7L,"C","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true),
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(4L,1L,3L,7L,"D","BENOME","IMPORT_FACTURE","CONTIENT",100,true),
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(5L,1L,3L,7L,"E","BENOME","OPERATION_BANCAIRE","CONTIENT",100,false),
                new fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto(6L,1L,3L,8L,"F","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true)));
        var regles=new ClassificationOperationBancaireService(tiers,factures).chargerReglesBancaires(c,personnes);
        assertThat(regles).hasSize(1);assertThat(regles.get(0).tiersId()).isEqualTo(7L);
    }

}
