package fr.wavy.tresorerie.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import fr.wavy.tresorerie.client.*;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.*;
import fr.wavy.tresorerie.modele.*;
import fr.wavy.tresorerie.repository.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "wavy.rapprochement.test.jdbc-url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${wavy.rapprochement.test.jdbc-url}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class PaiementAchatRapprochementIntegrationTest {
    final ContexteRequete c=new ContexteRequete(2903L,2904L,2906L,List.of("ADMIN_TENANT"),null);
    @Autowired PaiementAchatRapprochementService paiements;
    @Autowired TresorerieService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired OperationBancaireRepository operations;
    @Autowired CompteBancaireRepository comptes;
    @Autowired RapprochementBancaireRepository rapprochements;
    @MockitoBean FacturesTresorerieClient factures;
    @MockitoBean TiersTresorerieClient tiers;
    Long op;
    @BeforeEach void avant() {
        nettoyer();
        CompteBancaire compte=new CompteBancaire(); compte.setTenantId(c.tenantId()); compte.setSocieteInterneId(c.societeCouranteId());
        compte.setNomCompte("Test isolé"); compte.setBanque("Test"); compte.setIban("FR-TEST-RAPPROCHEMENT"); compte=comptes.saveAndFlush(compte);
        OperationBancaire o=new OperationBancaire(); o.setTenantId(c.tenantId());o.setSocieteInterneId(c.societeCouranteId());
        o.setCompteBancaire(compte);o.setDateOperation(LocalDate.of(2026,2,1));o.setLibelle("Facture fournisseur");
        o.setSens(SensOperation.DEBIT);o.setMontant(new BigDecimal("100.00"));o.setDebit(new BigDecimal("100.00"));
        o.setTiersReconnu("Tiers test");o.setHashOperation("a".repeat(64));o.setTiersId(7L);o.setTypeTiersReconnu(TypeTiersReconnu.FOURNISSEUR);
        o.setNatureOperation(NatureOperationBancaire.FACTURE_FOURNISSEUR);op=operations.saveAndFlush(o).getId();
        when(factures.listerFacturesOuvertes(any(),eq(TypeFactureRapprochement.ACHAT),eq(7L)))
            .thenReturn(List.of(facture(10L),facture(11L)));
        when(factures.listerFacturesAchatNotesFraisOuvertes(any(),eq(7L),any())).thenReturn(List.of(facture(20L)));
    }
    @AfterEach void nettoyer() {
        jdbc.update("delete from rapprochement_bancaire where tenant_id=2903");
        jdbc.update("delete from operation_bancaire where tenant_id=2903");
        jdbc.update("delete from compte_bancaire where tenant_id=2903");
    }
    FactureOuverteRapprochementDto facture(Long id) {
        return new FactureOuverteRapprochementDto(id,"ACHAT",7L,"FA-"+id,LocalDate.now(),null,new BigDecimal("100.00"),"Facture","Fournisseur");
    }
    Long preparer(Long facture,String montant) {return paiements.preparer(c,op,facture,new BigDecimal(montant),100,"RAP-"+facture,"MANUEL");}
    PieceCarteRapprochementDto carte() {
        return new PieceCarteRapprochementDto(10L,c.tenantId(),c.societeCouranteId(),7L,"FA-10",
                LocalDate.of(2026,1,31),LocalDate.of(2020,1,31),new BigDecimal("100.00"),"SSP France","SSP France","Pièce carte");
    }
    ConfirmationCarteDemande confirmation(boolean affectation, boolean date) {
        return new ConfirmationCarteDemande(10L,7L,true,affectation,date);
    }
    void sansTiers() {
        jdbc.update("update operation_bancaire set tiers_id=null,type_tiers_reconnu=null,nature_operation='SALAIRE',libelle='Paiement par carte BRIOCHE DOREE 31/01' where id=?",op);
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(carte()));
    }
    @Test void previsualisationSansEcritureEtConfirmationAvecTiersExplicite() {
        sansTiers();
        var resultat=service.proposerCartes(c);
        assertThat(resultat.operationsExaminees()).isEqualTo(1);assertThat(resultat.propositions()).hasSize(1);
        assertThat(resultat.propositions().get(0).commercantReconnu()).isFalse();
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).isEmpty();
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(false,false))).hasMessageContaining("explicitement");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        Long id=paiements.confirmerCarte(c,op,confirmation(true,false));
        assertThat(paiements.confirmerCarte(c,op,confirmation(true,false))).isEqualTo(id);
        var apres=operations.findById(op).orElseThrow();assertThat(apres.getTiersId()).isEqualTo(7L);
        assertThat(apres.getNatureOperation()).isEqualTo(NatureOperationBancaire.FACTURE_FOURNISSEUR);
        assertThat(apres.isRapprochee()).isTrue();verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void choixExpliciteCarteNeDevientPasUnRemboursementSelonLeLibelle() {
        sansTiers();jdbc.update("update operation_bancaire set libelle='Paiement carte note de frais 31/01' where id=?",op);
        paiements.confirmerCarte(c,op,confirmation(true,false));
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(10L),argThat(d->Boolean.FALSE.equals(d.remboursementBeneficiaire())));
    }
    @Test void ecartDUnCentimeNeSoldePasSilencieusementLeReliquat() {
        sansTiers();jdbc.update("update operation_bancaire set debit=100.01,montant=100.01 where id=?",op);
        var p=service.proposerCartes(c).propositions().get(0);
        assertThat(p.ecart()).isEqualByComparingTo("0.01");assertThat(p.montantRapproche()).isEqualByComparingTo("100");
        paiements.confirmerCarte(c,op,confirmation(true,false));
        assertThat(operations.findById(op).orElseThrow().getStatutRapprochement()).isEqualTo(StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(10L),argThat(d->d.montant().compareTo(new BigDecimal("100"))==0));
    }
    @Test void confirmationObligatoireEtPieceEtrangereRefusee() {
        sansTiers();
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,new ConfirmationCarteDemande(10L,7L,false,true,false))).hasMessageContaining("Confirmation");
        var p=carte();
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(new PieceCarteRapprochementDto(p.id(),999L,p.societeInterneId(),p.tiersId(),p.numeroPiece(),p.dateDepense(),p.datePiece(),p.montant(),p.nomTiers(),p.nomMarchand(),p.libelle())));
        assertThat(service.proposerCartes(c).propositions()).isEmpty();
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(true,false))).hasMessageContaining("société");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void parcoursVenteNeDependPasDeLaRechercheCarte() {
        jdbc.update("update operation_bancaire set sens='CREDIT',credit=100,debit=0,nature_operation='FACTURE_CLIENT',type_tiers_reconnu='CLIENT' where id=?",op);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.VENTE,7L)).thenReturn(List.of(facture(10L)));
        service.rapprocherFacture(c,op);
        verify(factures,never()).listerCartesOuvertes(any());
    }
    @Test void absenceDeMatchEtAucuneValidationAutomatiqueCarte() {
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(carte()));
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of(facture(10L)));
        assertThat(service.rapprocherFacture(c,op).rapprochementsCrees()).isZero();
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        jdbc.update("update operation_bancaire set debit=900 where id=?",op);
        assertThat(service.proposerCartes(c).operationsSansMatch()).isEqualTo(1);
    }
    @Test void refuseTiersDifferentEtContexteEtranger() {
        sansTiers();jdbc.update("update operation_bancaire set tiers_id=8 where id=?",op);
        assertThat(service.proposerCartes(c).propositions().get(0).conflit()).isTrue();
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(true,false))).hasMessageContaining("tiers bancaire");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isEqualTo(8L);
        for(var autre:List.of(new ContexteRequete(999L,c.utilisateurId(),c.societeCouranteId(),c.roles(),null),
                new ContexteRequete(c.tenantId(),c.utilisateurId(),999L,c.roles(),null)))
            assertThatThrownBy(()->paiements.confirmerCarte(autre,op,confirmation(true,false))).hasMessageContaining("introuvable");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void dateAtypiqueRequiertUnConsentementDistinct() {
        sansTiers();var p=carte();
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(new PieceCarteRapprochementDto(p.id(),p.tenantId(),p.societeInterneId(),p.tiersId(),p.numeroPiece(),LocalDate.of(2025,1,1),p.datePiece(),p.montant(),p.nomTiers(),p.nomMarchand(),p.libelle())));
        assertThat(service.proposerCartes(c).propositions().get(0).dateAtypique()).isTrue();
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(true,false))).hasMessageContaining("date atypique");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        paiements.confirmerCarte(c,op,confirmation(true,true));
    }
    @Test void pieceChangeeApresPrevisualisationNeChangePasLeTiers() {
        sansTiers();service.proposerCartes(c);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of());
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(true,false))).hasMessageContaining("validée");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void carteReponsePerdueReprendLeMemePaiement() {
        sansTiers();var perdre=new AtomicBoolean(true);var recus=ConcurrentHashMap.<Long>newKeySet();
        when(factures.enregistrerPaiement(any(),any(),anyLong(),any())).thenAnswer(i->{
            EnregistrementPaiementFactureDto d=i.getArgument(3);recus.add(d.rapprochementId());
            if(perdre.getAndSet(false))throw new IllegalStateException("Réponse perdue");return facture(10L);
        });
        assertThatThrownBy(()->paiements.confirmerCarte(c,op,confirmation(true,false))).hasMessageContaining("perdue");
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of());
        paiements.confirmerCarte(c,op,confirmation(true,false));assertThat(recus).hasSize(1);
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isTrue();
    }
    Long deuxiemeCarte() {
        var original=operations.findById(op).orElseThrow();var o=new OperationBancaire();
        o.setTenantId(c.tenantId());o.setSocieteInterneId(c.societeCouranteId());o.setCompteBancaire(original.getCompteBancaire());
        o.setDateOperation(original.getDateOperation());o.setDebit(new BigDecimal("100.00"));o.setMontant(new BigDecimal("100.00"));
        o.setLibelle("Paiement carte 31/01");o.setHashOperation("b".repeat(64));return operations.saveAndFlush(o).getId();
    }
    @Test void ambiguiteSignaleeEtConflitRefuseSansSecondPaiement() {
        sansTiers();Long autre=deuxiemeCarte();
        var propositions=service.proposerCartes(c).propositions();assertThat(propositions).hasSize(2).allMatch(PropositionCarteDto::ambiguite);
        paiements.confirmerCarte(c,op,confirmation(true,false));
        assertThatThrownBy(()->paiements.confirmerCarte(c,autre,confirmation(true,false))).hasMessageContaining("réserve");
        assertThat(operations.findById(autre).orElseThrow().getTiersId()).isNull();
        verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void confirmationsCarteConcurrentesSurDeuxOperationsNePaientQuUneFois() throws Exception {
        sansTiers();Long autre=deuxiemeCarte();var pool=Executors.newFixedThreadPool(2);var depart=new CountDownLatch(1);
        try {
            var appels=List.of(op,autre).stream().map(id->pool.submit(()->{
                depart.await();try {paiements.confirmerCarte(c,id,confirmation(true,false));return true;}
                catch(fr.wavy.tresorerie.exception.RegleMetierException e){return false;}
            })).toList();depart.countDown();int succes=0;for(var appel:appels)if(appel.get(20,TimeUnit.SECONDS))succes++;
            assertThat(succes).isEqualTo(1);verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
        }finally{pool.shutdownNow();}
    }
    @Test void propositionRepeteeReutiliseLeMemeIdentifiant() {
        Long id=preparer(10L,"40.00");assertThat(preparer(10L,"40.00")).isEqualTo(id);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).hasSize(1);
    }
    @Test void validationPartiellePuisTotaleEtRefusDoubleValidation() {
        Long a=preparer(10L,"40.00");paiements.valider(c,a);
        assertThat(operations.findById(op).orElseThrow().getStatutRapprochement()).isEqualTo(StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
        Long b=preparer(11L,"60.00");paiements.valider(c,b);
        assertThat(operations.findById(op).orElseThrow().getStatutRapprochement()).isEqualTo(StatutOperationBancaire.RAPPROCHEE);
        assertThatThrownBy(()->paiements.valider(c,a)).hasMessageContaining("proposition");
        verify(factures,times(2)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void refuseDepassementAvantAppelPaiement() {
        Long a=preparer(10L,"60.00"),b=preparer(11L,"60.00");paiements.valider(c,a);
        assertThatThrownBy(()->paiements.valider(c,b)).hasMessageContaining("disponible");
        verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void reponsePerdueConserveUneIntentionRejouableEtNonAnnulable() {
        Long id=preparer(10L,"100.00"); var recus=ConcurrentHashMap.<Long>newKeySet();var perdre=new AtomicBoolean(true);
        when(factures.enregistrerPaiement(any(),any(),anyLong(),any())).thenAnswer(i->{
            EnregistrementPaiementFactureDto d=i.getArgument(3);recus.add(d.rapprochementId());
            if(perdre.getAndSet(false))throw new IllegalStateException("Réponse perdue après paiement");return facture(10L);
        });
        assertThatThrownBy(()->paiements.valider(c,id)).hasMessageContaining("perdue");
        assertThat(rapprochements.findById(id).orElseThrow().isPaiementEngage()).isTrue();
        assertThat(rapprochements.findById(id).orElseThrow().getStatut()).isEqualTo(StatutRapprochement.PROPOSE);
        assertThatThrownBy(()->paiements.annuler(c,id,null)).hasMessageContaining("repris");
        paiements.valider(c,id);assertThat(recus).containsExactly(id);
        assertThat(rapprochements.findById(id).orElseThrow().getStatut()).isEqualTo(StatutRapprochement.VALIDE);
    }
    @Test void remboursementRechercheLeBeneficiaireEtBloquePieceTechnique() {
        jdbc.update("update operation_bancaire set nature_operation='NOTE_DE_FRAIS',type_tiers_reconnu='SALARIE',libelle='NDF remboursement salarié' where id=?",op);
        assertThat(service.listerFacturesCandidates(c,op)).extracting(FactureOuverteRapprochementDto::id).containsExactly(20L);
        assertThatThrownBy(()->service.creerPieceRapprochement(c,op)).hasMessageContaining("remboursement");
        service.rapprocherManuellement(c,op,new RapprochementManuelDemande(List.of(new LigneRapprochementManuelDemande(20L,TypeFactureRapprochement.ACHAT,new BigDecimal("100.00")))));
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(20L),argThat(d->Boolean.TRUE.equals(d.remboursementBeneficiaire())&&d.tiersId().equals(7L)));
    }
    @Test void endpointGeneriqueNeContournePasLesCandidatsNiLePlafond() {
        assertThatThrownBy(()->service.creerRapprochement(c,new RapprochementBancaireDemande(op,999L,TypeFactureRapprochement.ACHAT,new BigDecimal("10.00"),null,StatutRapprochement.VALIDE,null))).hasMessageContaining("validée");
        assertThatThrownBy(()->service.creerRapprochement(c,new RapprochementBancaireDemande(op,10L,TypeFactureRapprochement.ACHAT,new BigDecimal("100.01"),null,StatutRapprochement.VALIDE,null))).hasMessageContaining("disponible");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void validationsConcurrentesNePaientQuUneFois() throws Exception {
        Long id=preparer(10L,"100.00");var pool=Executors.newFixedThreadPool(2);var depart=new CountDownLatch(1);
        try {
            var appels=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{
                depart.await();try {paiements.valider(c,id);return true;}catch(fr.wavy.tresorerie.exception.RegleMetierException e){return false;}
            })).toList();depart.countDown();
            int succes=0;for(var appel:appels)if(appel.get(20,TimeUnit.SECONDS))succes++;
            assertThat(succes).isEqualTo(1);verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
        }finally{pool.shutdownNow();}
    }
    @Test void validationGeneriqueMetAJourLaFactureEtLOperation() {
        var r=service.creerRapprochement(c,new RapprochementBancaireDemande(op,10L,TypeFactureRapprochement.ACHAT,new BigDecimal("100.00"),null,StatutRapprochement.VALIDE,null));
        assertThat(r.statut()).isEqualTo(StatutRapprochement.VALIDE);
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isTrue();
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(10L),argThat(d->d.rapprochementId().equals(r.id())&&d.operationBancaireId().equals(op)));
    }
    @Test void montantEncoreDuSeulEstRapprochable() {
        when(factures.listerFacturesOuvertes(any(),eq(TypeFactureRapprochement.ACHAT),eq(7L))).thenReturn(List.of(
            new FactureOuverteRapprochementDto(10L,"ACHAT",7L,"FA-10",LocalDate.now(),null,new BigDecimal("30.00"),"Facture","Tiers")));
        assertThatThrownBy(()->preparer(10L,"30.01")).hasMessageContaining("reste");
    }
    @Test void automatiqueNeRepetePasLesPropositions() {
        service.rapprocherFacture(c,op);service.rapprocherFacture(c,op);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).hasSize(1);
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void automatiqueReprendMemeSiLaFactureEstDejaSoldeeADistance() {
        var f=new FactureOuverteRapprochementDto(10L,"ACHAT",7L,"FA-10",LocalDate.of(2026,2,1),LocalDate.of(2026,2,1),new BigDecimal("100.00"),"Facture","Tiers");
        when(factures.listerFacturesOuvertes(any(),eq(TypeFactureRapprochement.ACHAT),eq(7L))).thenReturn(List.of(f));
        var perdre=new AtomicBoolean(true);var recus=ConcurrentHashMap.<Long>newKeySet();
        when(factures.enregistrerPaiement(any(),any(),anyLong(),any())).thenAnswer(i->{
            EnregistrementPaiementFactureDto d=i.getArgument(3);recus.add(d.rapprochementId());
            if(perdre.getAndSet(false)) {
                when(factures.listerFacturesOuvertes(any(),eq(TypeFactureRapprochement.ACHAT),eq(7L))).thenReturn(List.of());
                throw new IllegalStateException("Réponse perdue");
            }return f;
        });
        assertThatThrownBy(()->service.rapprocherFacture(c,op)).hasMessageContaining("perdue");
        service.rapprocherFacture(c,op);
        assertThat(recus).hasSize(1);assertThat(operations.findById(op).orElseThrow().isRapprochee()).isTrue();
    }
    @Test void factureDevenueIneligibleEstRefuseeAvantEngagement() {
        Long id=preparer(10L,"50.00");
        when(factures.listerFacturesOuvertes(any(),eq(TypeFactureRapprochement.ACHAT),eq(7L))).thenReturn(List.of());
        assertThatThrownBy(()->paiements.valider(c,id)).hasMessageContaining("éligible");
        assertThat(rapprochements.findById(id).orElseThrow().isPaiementEngage()).isFalse();
        paiements.annuler(c,id,"Facture annulée");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void operationEngageeNePeutPasChangerDeContrepartie() {
        Long id=preparer(10L,"100.00");when(factures.enregistrerPaiement(any(),any(),anyLong(),any())).thenThrow(new IllegalStateException("Réponse perdue"));
        assertThatThrownBy(()->paiements.valider(c,id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.modifierClassificationOperation(c,op,new ClassificationOperationDemande(NatureOperationBancaire.SALAIRE))).hasMessageContaining("paiement");
    }
    @Test void anciennePropositionSalaireNePeutPasPayerUnRemboursement() {
        jdbc.update("update operation_bancaire set nature_operation='NOTE_DE_FRAIS',libelle='REMBOURSEMENT NDF' where id=?",op);
        RapprochementBancaire r=new RapprochementBancaire();r.setTenantId(c.tenantId());r.setSocieteInterneId(c.societeCouranteId());
        r.setOperationBancaire(operations.findById(op).orElseThrow());r.setTypeFacture(TypeFactureRapprochement.SALAIRE);
        r.setTypePiece(TypePieceRapprochement.SALAIRE);r.setSalaireId(88L);r.setMontantRapproche(new BigDecimal("100.00"));r.setEcart(BigDecimal.ZERO);
        Long id=rapprochements.saveAndFlush(r).getId();
        assertThatThrownBy(()->service.validerRapprochementPropose(c,id)).hasMessageContaining("salaire");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void creationsConcurrentesNeDupliquentPasLaProposition() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var depart=new CountDownLatch(1);
        try {var a=pool.submit(()->{depart.await();return preparer(10L,"100.00");});var b=pool.submit(()->{depart.await();return preparer(10L,"100.00");});depart.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
    }
}
