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
import fr.wavy.tresorerie.dto.AssistantRapprochementDto.*;
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
    @Autowired ImportReleveBancaireService importBancaire;
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
        jdbc.update("delete from piece_rapprochement where tenant_id=2903");
        jdbc.update("delete from compteur_piece_rapprochement where tenant_id=2903");
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
        tiersAssistant(7L, "Salarié", "SALARIE", false, null);
        jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS',nature_operation='NOTE_DE_FRAIS',type_tiers_reconnu='SALARIE',libelle='NDF remboursement salarié' where id=?",op);
        assertThat(service.listerFacturesCandidates(c,op)).extracting(FactureOuverteRapprochementDto::id).containsExactly(20L);
        assertThatThrownBy(()->service.creerPieceRapprochement(c,op)).hasMessageContaining("remboursement");
        service.rapprocherManuellement(c,op,new RapprochementManuelDemande(List.of(new LigneRapprochementManuelDemande(20L,TypeFactureRapprochement.ACHAT,new BigDecimal("100.00")))));
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(20L),argThat(d->Boolean.TRUE.equals(d.remboursementBeneficiaire())&&d.tiersId().equals(7L)));
    }
    @Test void rapprochementManuelProposeToutesLesPeriodesEtConfirmeUnePaieEloignee() {
        tiersAssistant(7L, "Salarié", "SALARIE", false, null);
        jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS',nature_operation='SALAIRE',type_tiers_reconnu='SALARIE' where id=?", op);
        var janvier = new FactureOuverteRapprochementDto(20L, "SALAIRE", 7L, "SAL-01", LocalDate.of(2026, 1, 1),
                null, new BigDecimal("100.00"), "Paie janvier", "Salarié");
        var novembre = new FactureOuverteRapprochementDto(21L, "SALAIRE", 7L, "SAL-11", LocalDate.of(2025, 11, 1),
                null, new BigDecimal("100.00"), "Paie novembre", "Salarié");
        when(factures.listerFacturesOuvertes(c, TypeFactureRapprochement.SALAIRE, 7L)).thenReturn(List.of(janvier, novembre));
        assertThat(service.listerFacturesCandidates(c, op)).extracting(FactureOuverteRapprochementDto::numeroFacture)
                .containsExactly("SAL-01", "SAL-11", "FA-20");
        var demande = new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(21L, TypeFactureRapprochement.SALAIRE, new BigDecimal("100.00"))));
        assertThat(service.rapprocherManuellement(c, op, demande).statutRapprochement()).isEqualTo(StatutOperationBancaire.RAPPROCHEE);
        assertThatThrownBy(() -> service.rapprocherManuellement(c, op, demande)).hasMessageContaining("plus ouverte");
        verify(factures, times(1)).enregistrerPaiement(eq(c), eq(TypeFactureRapprochement.SALAIRE), eq(21L), any());
    }

    @Test void rapprochementManuelCarteIgnoreLaDateMaisConserveLaReservation() {
        tiersAssistant(7L, "Marchand", "MARCHAND", false, null);
        jdbc.update("update operation_bancaire set type_operation='PAIEMENT_CB' where id=?", op);
        var carte = new FactureOuverteRapprochementDto(10L, "ACHAT", 7L, "CARTE-10", LocalDate.of(2020, 1, 1),
                null, new BigDecimal("100.00"), "Autre libellé", "Marchand", new BigDecimal("100.00"),
                "A_RAPPROCHER", "DEPENSE_CARTE_ENTREPRISE");
        when(factures.listerFacturesOuvertes(c, TypeFactureRapprochement.ACHAT, 7L)).thenReturn(List.of(carte));
        assertThat(service.listerFacturesCandidates(c, op)).containsExactly(carte);
        var demande = new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(10L, TypeFactureRapprochement.ACHAT, new BigDecimal("100.00"))));
        Long secondId = deuxiemeCarte();
        jdbc.update("update operation_bancaire set tiers_id=7,type_operation='PAIEMENT_CB',type_tiers_reconnu='FOURNISSEUR' where id=?", secondId);
        service.rapprocherManuellement(c, op, demande);
        assertThatThrownBy(() -> service.rapprocherManuellement(c, secondId, demande)).hasMessageContaining("réserve");
        verify(factures, times(1)).enregistrerPaiement(eq(c), eq(TypeFactureRapprochement.ACHAT), eq(10L),
                argThat(d -> Boolean.FALSE.equals(d.remboursementBeneficiaire())));
    }

    @Test void rapprochementManuelClientRevalideLeTiersLeMontantEtLaSociete() {
        tiersAssistant(7L, "Client", "CLIENT", false, null);
        jdbc.update("update operation_bancaire set sens='CREDIT',credit=100,debit=0,type_tiers_reconnu='CLIENT' where id=?", op);
        var vente = new FactureOuverteRapprochementDto(12L, "VENTE", 7L, "FV-12", LocalDate.of(2020, 1, 1),
                null, new BigDecimal("80.00"), "Facture", "Client");
        when(factures.listerFacturesOuvertes(c, TypeFactureRapprochement.VENTE, 7L)).thenReturn(List.of(vente));
        var montantExcessif = new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(12L, TypeFactureRapprochement.VENTE, new BigDecimal("90.00"))));
        assertThatThrownBy(() -> service.rapprocherManuellement(c, op, montantExcessif)).hasMessageContaining("solde de la pièce");
        var autreSociete = new ContexteRequete(c.tenantId(), c.utilisateurId(), 999L, c.roles(), null);
        assertThatThrownBy(() -> service.listerFacturesCandidates(autreSociete, op)).hasMessageContaining("introuvable");
        var demande = new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(12L, TypeFactureRapprochement.VENTE, new BigDecimal("80.00"))));
        assertThat(service.rapprocherManuellement(c, op, demande).resteARapprocher()).isEqualByComparingTo("20");
        verify(factures, times(1)).enregistrerPaiement(eq(c), eq(TypeFactureRapprochement.VENTE), eq(12L), any());
    }

    @Test void annulationPaieRejoueLeMemeRecuApresUneReponsePerduePuisPermetLeRapprochementManuel() {
        tiersAssistant(7L, "Salarié fictif", "SALARIE", false, null);
        jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS',nature_operation='SALAIRE',type_tiers_reconnu='SALARIE' where id=?", op);
        var janvier = new FactureOuverteRapprochementDto(31L, "SALAIRE", 7L, "PAIE-01", LocalDate.of(2026, 1, 1),
                null, new BigDecimal("100.00"), "Salaire janvier", "Salarié fictif");
        var novembre = new FactureOuverteRapprochementDto(32L, "SALAIRE", 7L, "PAIE-11", LocalDate.of(2025, 11, 1),
                null, new BigDecimal("100.00"), "Salaire novembre", "Salarié fictif");
        when(factures.listerFacturesOuvertes(c, TypeFactureRapprochement.SALAIRE, 7L)).thenReturn(List.of(janvier, novembre));
        service.rapprocherManuellement(c, op, new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(31L, TypeFactureRapprochement.SALAIRE, new BigDecimal("100.00")))));
        var initial = rapprochements.findByOperationBancaireIdAndStatut(op, StatutRapprochement.VALIDE).get(0);
        var demande = new AnnulationPaiementRapprochementDto(initial.getId(), op, new BigDecimal("100.00"), initial.getLibelleRapprochement());
        doThrow(new IllegalStateException("Réponse perdue après annulation")).doNothing()
                .when(factures).annulerPaiement(c, TypeFactureRapprochement.SALAIRE, 31L, demande);
        assertThatThrownBy(() -> service.annulerRapprochementValide(c, initial.getId())).hasMessageContaining("perdue");
        assertThat(rapprochements.findById(initial.getId()).orElseThrow().getStatut()).isEqualTo(StatutRapprochement.VALIDE);
        assertThat(operations.findById(op).orElseThrow().getMontantRapproche()).isEqualByComparingTo("100");
        assertThat(service.annulerRapprochementValide(c, initial.getId()).statut()).isEqualTo(StatutRapprochement.ANNULE);
        service.annulerRapprochementValide(c, initial.getId());
        verify(factures, times(2)).annulerPaiement(c, TypeFactureRapprochement.SALAIRE, 31L, demande);
        var operation = operations.findById(op).orElseThrow();
        assertThat(operation.getMontantRapproche()).isZero();
        assertThat(operation.getStatutRapprochement()).isEqualTo(StatutOperationBancaire.NON_RAPPROCHEE);
        assertThat(operation.getTiersId()).isEqualTo(7L);
        assertThat(operation.getSalaireId()).isNull();
        assertThat(service.listerFacturesCandidates(c, op)).filteredOn(f -> "SALAIRE".equals(f.typeFacture()))
                .containsExactly(janvier, novembre);
        service.rapprocherManuellement(c, op, new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(32L, TypeFactureRapprochement.SALAIRE, new BigDecimal("100.00")))));
        assertThat(operations.findById(op).orElseThrow().getSalaireId()).isEqualTo(32L);
        assertThat(rapprochements.findById(initial.getId()).orElseThrow().getUtilisateurAnnulationId()).isEqualTo(c.utilisateurId());
    }

    @Test void annulationUnitaireConserveAutreAssociationEtRefusePaiementIncertain() {
        tiersAssistant(7L, "Fournisseur fictif", "FOURNISSEUR_GENERAL", false, null);
        service.rapprocherManuellement(c, op, new RapprochementManuelDemande(List.of(
                new LigneRapprochementManuelDemande(10L, TypeFactureRapprochement.ACHAT, new BigDecimal("60.00")),
                new LigneRapprochementManuelDemande(11L, TypeFactureRapprochement.ACHAT, new BigDecimal("40.00")))));
        var initial = rapprochements.findByOperationBancaireIdAndStatut(op, StatutRapprochement.VALIDE).stream()
                .filter(r -> r.getFactureId().equals(11L)).findFirst().orElseThrow();
        var demande = new AnnulationPaiementRapprochementDto(initial.getId(), op, new BigDecimal("40.00"), initial.getLibelleRapprochement());
        doThrow(new fr.wavy.tresorerie.exception.RegleMetierException("PAIEMENT_ORIGINE_NON_IDENTIFIE", "Paiement sans reçu certain"))
                .doNothing().when(factures).annulerPaiement(c, TypeFactureRapprochement.ACHAT, 11L, demande);
        assertThatThrownBy(() -> service.annulerRapprochementValide(c, initial.getId())).hasMessageContaining("sans reçu certain");
        assertThat(rapprochements.findById(initial.getId()).orElseThrow().getStatut()).isEqualTo(StatutRapprochement.VALIDE);
        service.annulerRapprochementsProposes(c);
        verify(factures, times(1)).annulerPaiement(c, TypeFactureRapprochement.ACHAT, 11L, demande);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op, StatutRapprochement.VALIDE)).hasSize(2);

        service.annulerRapprochementValide(c, initial.getId());
        var operation = operations.findById(op).orElseThrow();
        assertThat(operation.getMontantRapproche()).isEqualByComparingTo("60");
        assertThat(operation.getStatutRapprochement()).isEqualTo(StatutOperationBancaire.PARTIELLEMENT_RAPPROCHEE);
        assertThat(operation.getFactureId()).isEqualTo(10L);
    }

    @Test void annulationCarteLibereSaReservationSansEffacerLeLienHistorique() {
        carteSure();
        Long rapprochement = paiements.confirmerCarte(c, op, confirmation(true, false));
        service.annulerRapprochementValide(c, rapprochement);
        var trace = rapprochements.findById(rapprochement).orElseThrow();
        assertThat(trace.getStatut()).isEqualTo(StatutRapprochement.ANNULE);
        assertThat(trace.getFactureId()).isEqualTo(10L);
        assertThat(paiements.confirmerCarte(c, op, confirmation(false, false))).isNotEqualTo(rapprochement);
    }

    @Test void assistantSalariéMultirolePrésentePaieEtRemboursementEtReprendLeTypeConfirmé() {
        when(tiers.listerTiersActifs(c)).thenReturn(List.of(new TiersTresorerieDto(7L,
                Set.of("SALARIE","PRESTATAIRE"), "Tiers fictif", null, null, "Tiers fictif", true, false,
                null, null, null, null, null)));
        jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS',nature_operation='FACTURE_FOURNISSEUR',statut_classification='CLASSEE_MANUELLE' where id=?", op);
        var salaire = new FactureOuverteRapprochementDto(20L, "SALAIRE", 7L, "SAL-20",
                LocalDate.of(2026,1,1),null,new BigDecimal("100.00"),"Paie","Tiers fictif");
        when(factures.listerSalairesOuverts(c,7L,2026,1,null)).thenReturn(List.of(salaire));
        var p = new Perimetre(null,null,null,null,null,null,null,null,true);
        var a = service.previsualiserAssistant(c,Etape.SALAIRES,null,p);
        assertThat(a.lignes().get(0).candidats()).extracting(Candidat::cle)
                .containsExactly("P:SALAIRE:20", "P:REMBOURSEMENT:20");
        assertThat(a.lignes().get(0).candidats()).noneMatch(Candidat::sur);
        var demande = new Confirmation(Etape.SALAIRES,p,null,true,List.of(choisir(a,1,false)));
        var perdu = new AtomicBoolean(true);
        when(factures.enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(20L),any())).thenAnswer(invocation -> {
            if (perdu.getAndSet(false)) throw new IllegalStateException("Réponse perdue");
            return facture(20L);
        });
        assertThat(service.confirmerAssistant(c,demande).aReprendre()).isEqualTo(1);
        when(factures.listerFacturesAchatNotesFraisOuvertes(c,7L,null)).thenReturn(List.of());
        jdbc.update("update operation_bancaire set nature_operation='SALAIRE',libelle='Virement' where id=?",op);
        var mauvaisType = new Confirmation(Etape.SALAIRES,p,null,true,List.of(choisir(a,0,false)));
        assertThat(service.confirmerAssistant(c,mauvaisType).reussis()).isZero();
        verify(factures,never()).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.SALAIRE),anyLong(),any());
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        var trace = rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE).get(0);
        assertThat(trace.getTypePiece()).isEqualTo(TypePieceRapprochement.REMBOURSEMENT_BENEFICIAIRE);
        verify(factures,times(2)).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(20L),
                argThat(d -> Boolean.TRUE.equals(d.remboursementBeneficiaire()) && trace.getId().equals(d.rapprochementId())));
    }

    @Test void assistantFournisseurNeBloquePasUneNatureSalaire() {
        tiersAssistant(7L,"Fournisseur","PRESTATAIRE",false,null);
        jdbc.update("update operation_bancaire set nature_operation='SALAIRE',statut_classification='CLASSEE_MANUELLE' where id=?",op);
        var p = new Perimetre(null,null,null,null,null,null,null,null,true);
        var a = service.previsualiserAssistant(c,Etape.FOURNISSEURS,null,p);
        assertThat(a.lignes().get(0).bloquee()).isFalse();
        assertThat(a.lignes().get(0).candidats()).hasSize(2);
        assertThat(service.confirmerAssistant(c,new Confirmation(Etape.FOURNISSEURS,p,null,true,List.of(choisir(a,0,false)))).reussis()).isEqualTo(1);
    }

    @Test void assistantClientAccepteNatureManuelleIncorrecte() {
        tiersAssistant(7L,"Client","CLIENT",false,null);
        jdbc.update("update operation_bancaire set sens='CREDIT',debit=0,credit=100,nature_operation='AUTRE',statut_classification='CLASSEE_MANUELLE' where id=?",op);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.VENTE,7L)).thenReturn(List.of(facture(10L)));
        var p = new Perimetre(null,null,null,null,null,null,null,null,true);
        var a = service.previsualiserAssistant(c,Etape.CLIENTS,null,p);
        assertThat(a.lignes().get(0).bloquee()).isFalse();
        assertThat(service.confirmerAssistant(c,new Confirmation(Etape.CLIENTS,p,null,true,List.of(choisir(a,0,false)))).reussis()).isEqualTo(1);
    }

    private void achatsAvecCarteOuverte() {
        var achatCarte = new FactureOuverteRapprochementDto(10L,"ACHAT",7L,"CARTE-10",LocalDate.of(2026,1,31),null,
                new BigDecimal("100.00"),"Carte","SSP",new BigDecimal("100.00"),"NON_PAYEE","DEPENSE_CARTE_ENTREPRISE");
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of(achatCarte, facture(11L)));
    }

    @Test void assistantMarchandCarteConserveLaClassificationEtLaReservation() {
        tiersAssistant(7L,"SSP France","MARCHAND",false,null);
        jdbc.update("update operation_bancaire set type_operation='PAIEMENT_CB',libelle='PAIEMENT SSP FRANCE 31/01',nature_operation='AUTRE',statut_classification='CLASSEE_MANUELLE' where id=?",op);
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(carte()));
        achatsAvecCarteOuverte();
        var p = new Perimetre(null,null,null,null,null,null,null,null,true);
        var a = service.previsualiserAssistant(c,Etape.FOURNISSEURS,null,p);
        assertThat(a.lignes().get(0).candidats()).extracting(Candidat::cle).containsExactly("C:10","P:ACHAT:11");
        assertThat(a.lignes().get(0).candidats().get(0).carte().affectationRequise()).isFalse();
        assertThat(service.confirmerAssistant(c,new Confirmation(Etape.FOURNISSEURS,p,null,true,List.of(choisir(a,0,false)))).reussis()).isEqualTo(1);
        var banque = operations.findById(op).orElseThrow();
        assertThat(banque.getNatureOperation()).isEqualTo(NatureOperationBancaire.AUTRE);
        assertThat(banque.getStatutClassification()).isEqualTo(StatutClassificationOperation.CLASSEE_MANUELLE);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE).get(0).getTypePiece())
                .isEqualTo(TypePieceRapprochement.DEPENSE_CARTE_ENTREPRISE);
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
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).isEmpty();
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
    ConfirmationCartesDemande lot(Long... ids) {
        var lignes = new ArrayList<ConfirmationCartesDemande.Ligne>();
        for (int i = 0; i < ids.length; i++) lignes.add(new ConfirmationCartesDemande.Ligne(ids[i],
                new ConfirmationCarteDemande(i == 0 ? 10L : 11L, 7L, true, true, false)));
        return new ConfirmationCartesDemande(true, lignes);
    }
    void carteSure() {
        sansTiers();
        jdbc.update("update operation_bancaire set libelle='PAIEMENT SSP FRANCE 31/01' where id=?",op);
    }
    Long lotDeuxPieces() {
        carteSure(); Long autre = deuxiemeCarte();
        jdbc.update("update operation_bancaire set debit=42,montant=42,libelle='PAIEMENT SSP FRANCE 31/01' where id=?",autre);
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(carte(),
                new PieceCarteRapprochementDto(11L,c.tenantId(),c.societeCouranteId(),7L,"FA-11",
                        LocalDate.of(2026,1,31),LocalDate.of(2026,1,31),new BigDecimal("42.00"),"SSP France","SSP France","Pièce carte")));
        return autre;
    }
    @Test void lotDeuxPiecesDistinctesEtDoubleSoumissionIdempotente() {
        Long autre = lotDeuxPieces(); var demande = lot(op,autre);
        var r = service.confirmerCartes(c,demande);
        assertThat(r.reussis()).isEqualTo(2);assertThat(r.refuses()).isZero();assertThat(r.conflits()).isZero();
        assertThat(service.confirmerCartes(c,demande).lignes()).extracting(ConfirmationCartesResultat.Ligne::rapprochementId)
                .containsExactlyElementsOf(r.lignes().stream().map(ConfirmationCartesResultat.Ligne::rapprochementId).toList());
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isTrue();
        assertThat(operations.findById(autre).orElseThrow().isRapprochee()).isTrue();
        verify(factures,times(2)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotRefuseToutesOccurrencesOperationOuPieceEnDoubleSansPaiement() {
        Long autre = lotDeuxPieces();
        for(var demande : List.of(lot(op,op),new ConfirmationCartesDemande(true,List.of(
                new ConfirmationCartesDemande.Ligne(op,confirmation(true,false)),
                new ConfirmationCartesDemande.Ligne(autre,confirmation(true,false)))))) {
            var r = service.confirmerCartes(c,demande);
            assertThat(r.conflits()).isEqualTo(2);assertThat(r.reussis()).isZero();
            assertThat(r.lignes()).allMatch(l -> l.code().equals("DOUBLON_CARTE"));
        }
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotIndigoFreeMobileMemeMontantResteAmbiguEtChoixIndividuelPossible() {
        carteSure(); Long autre = deuxiemeCarte();
        jdbc.update("update operation_bancaire set libelle='PAIEMENT INDIGO 31/01',debit=2,montant=2 where id=?",op);
        jdbc.update("update operation_bancaire set libelle='PAIEMENT FREE MOBILE 31/01',debit=2,montant=2 where id=?",autre);
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(new PieceCarteRapprochementDto(10L,c.tenantId(),c.societeCouranteId(),7L,"FA-10",
                LocalDate.of(2026,1,31),LocalDate.of(2026,1,31),new BigDecimal("2.00"),"INDIGO","INDIGO","Parking")));
        assertThat(service.proposerCartes(c).propositions()).hasSize(2).allMatch(PropositionCarteDto::ambiguite);
        assertThat(service.confirmerCartes(c,lot(op)).lignes().get(0).code()).isEqualTo("AMBIGUITE_CARTE");
        assertThat(service.confirmerCartes(c,lot(autre)).lignes().get(0).code()).isEqualTo("VERIFICATION_INDIVIDUELLE_CARTE");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        paiements.confirmerCarte(c,op,confirmation(true,false));
        assertThat(operations.findById(autre).orElseThrow().isRapprochee()).isFalse();
    }
    @Test void lotCommercantDifferentDateAtypiqueEtCentimeDemandentUnChoixIndividuel() {
        sansTiers();
        assertThat(service.confirmerCartes(c,lot(op)).lignes().get(0).code()).isEqualTo("VERIFICATION_INDIVIDUELLE_CARTE");
        carteSure();jdbc.update("update operation_bancaire set date_operation='2026-03-01' where id=?",op);
        assertThat(service.confirmerCartes(c,lot(op)).lignes().get(0).code()).isEqualTo("VERIFICATION_INDIVIDUELLE_CARTE");
        jdbc.update("update operation_bancaire set date_operation='2026-02-01',debit=100.01,montant=100.01 where id=?",op);
        assertThat(service.confirmerCartes(c,lot(op)).lignes().get(0).code()).isEqualTo("VERIFICATION_INDIVIDUELLE_CARTE");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotEchecPartielContinueEtExposeLeConflitDeTiers() {
        Long autre = lotDeuxPieces();jdbc.update("update operation_bancaire set tiers_id=99 where id=?",op);
        var r = service.confirmerCartes(c,lot(op,autre));
        assertThat(r.reussis()).isEqualTo(1);assertThat(r.conflits()).isEqualTo(1);
        assertThat(r.lignes().get(0).code()).isEqualTo("TIERS_CARTE_DIFFERENT");
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isEqualTo(99L);
        verify(factures,times(1)).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotRevalideLeDisponibleEtLaPieceApresPrevisualisation() {
        Long autre = lotDeuxPieces();service.proposerCartes(c);
        jdbc.update("update operation_bancaire set statut_rapprochement='PARTIELLEMENT_RAPPROCHEE' where id=?",op);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of(
                new FactureOuverteRapprochementDto(11L,"ACHAT",7L,"FA-11",LocalDate.now(),null,new BigDecimal("30.00"),"Pièce","SSP France")));
        var r = service.confirmerCartes(c,lot(op,autre));
        assertThat(r.reussis()).isZero();assertThat(r.refuses()).isEqualTo(2);
        assertThat(r.lignes()).extracting(ConfirmationCartesResultat.Ligne::code)
                .containsExactly("CARTE_NON_ELIGIBLE","MONTANT_SUPERIEUR_RESTE_FACTURE");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotIsoleTenantSocieteEtExigeConsentement() {
        carteSure();
        assertThatThrownBy(() -> service.confirmerCartes(c,new ConfirmationCartesDemande(false,lot(op).lignes())))
                .hasMessageContaining("Confirmez");
        for(var autre : List.of(new ContexteRequete(999L,c.utilisateurId(),c.societeCouranteId(),c.roles(),null),
                new ContexteRequete(c.tenantId(),c.utilisateurId(),999L,c.roles(),null))) {
            assertThat(service.confirmerCartes(autre,lot(op)).lignes().get(0).code()).isEqualTo("OPERATION_INTROUVABLE");
        }
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void lotReponsePerdueNeMasquePasAutresLignesEtReprendMemeRecu() {
        Long autre = lotDeuxPieces();var perdre = new AtomicBoolean(true);var recus = ConcurrentHashMap.<Long>newKeySet();
        when(factures.enregistrerPaiement(any(),any(),anyLong(),any())).thenAnswer(i -> {
            EnregistrementPaiementFactureDto d = i.getArgument(3);recus.add(d.rapprochementId());
            if (perdre.getAndSet(false)) throw new IllegalStateException("Réponse perdue après paiement");
            return facture(i.getArgument(2));
        });
        var r = service.confirmerCartes(c,lot(op,autre));
        assertThat(r.refuses()).isEqualTo(1);assertThat(r.reussis()).isEqualTo(1);
        assertThat(r.lignes().get(0).code()).isEqualTo("RESULTAT_A_REPRENDRE");
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of());
        assertThat(service.confirmerCartes(c,lot(op,autre)).reussis()).isEqualTo(2);
        assertThat(recus).hasSize(2);assertThat(rapprochements.count()).isGreaterThanOrEqualTo(2);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE)).hasSize(1);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(autre,StatutRapprochement.VALIDE)).hasSize(1);
    }
    fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre perimetre() {
        return new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre(null,null,null,null,null,null);
    }
    private Perimetre consultation() {
        return new Perimetre(null,null,null,null,null,null,null,null,true);
    }

    @Test void salariesUtiliseLaPieceDeRemboursementPourLesFraisDeDeplacement() {
        tiersAssistant(7L,"Salarié","SALARIE",false,null);
        jdbc.update("""
                update operation_bancaire set type_tiers_reconnu='SALARIE', nature_operation='NOTE_DE_FRAIS',
                type_operation='VIREMENT_EMIS', libelle='Frais de déplacement' where id=?
                """,op);
        var a=service.previsualiserAssistant(c,Etape.SALAIRES,"CARTE",consultation());
        assertThat(a.lignes().get(0).candidats()).extracting(Candidat::cle).containsExactly("P:REMBOURSEMENT:20");
        verify(factures).listerSalairesOuverts(eq(c),eq(7L),anyInt(),anyInt(),any());
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        var demande=new Confirmation(Etape.SALAIRES,consultation(),"CARTE",true,List.of(choisir(a,0,false)));
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.ACHAT),eq(20L),argThat(d -> d.remboursementBeneficiaire()));
    }

    @Test void fournisseursProposeLaCarteDuMarchandAvantLesFacturesOrdinaires() {
        tiersAssistant(7L,"SSP France","MARCHAND",false,null);
        jdbc.update("""
                update operation_bancaire set type_operation='PAIEMENT_CB', libelle='CB SSP France 31/01'
                where id=?
                """,op);
        when(factures.listerCartesOuvertes(c)).thenReturn(List.of(carte()));
        achatsAvecCarteOuverte();
        var a=service.previsualiserAssistant(c,Etape.FOURNISSEURS,"CARTE",consultation());
        assertThat(a.lignes().get(0).candidats()).extracting(Candidat::cle).containsExactly("C:10","P:ACHAT:11");
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        var demande=new Confirmation(Etape.FOURNISSEURS,consultation(),"CARTE",true,List.of(choisir(a,0,false)));
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE))
                .extracting(RapprochementBancaire::getModeRapprochement).containsExactly("ROLE_CARTE");
    }

    @Test void clientsProposeUnePieceTresoreriePourUnEncaissementExonere() {
        jdbc.update("""
                update operation_bancaire set sens='CREDIT',debit=0,credit=100,
                type_tiers_reconnu='CLIENT',type_operation='VIREMENT_RECU',nature_operation='FACTURE_CLIENT',
                statut_classification='CLASSEE_MANUELLE' where id=?
                """,op);
        tiersAssistant(7L,"Client","CLIENT",false,"EXONERATION");
        var refuse=service.previsualiserAssistant(c,Etape.CLIENTS,"CARTE",consultation());
        assertThat(refuse.lignes().get(0).candidats()).isEmpty();
        tiersAssistant(7L,"Client","CLIENT",true,"EXONERATION");
        var a=service.previsualiserAssistant(c,Etape.CLIENTS,"CARTE",consultation());
        assertThat(a.lignes().get(0).candidats()).extracting(Candidat::cle).containsExactly("CREATION");
        var demande=new Confirmation(Etape.CLIENTS,consultation(),"CARTE",true,List.of(choisir(a,0,false)));
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(operations.findById(op).orElseThrow().getStatutClassification()).isEqualTo(StatutClassificationOperation.CLASSEE_MANUELLE);
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }

    @Test void fournisseursProposeLaPieceFiscalePuisLaReprendSansDoublon() {
        impotAutorise();
        var perimetre=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre(null,null,null,null,null,null,null,null,true);
        var etape=fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.FOURNISSEURS;
        var apercu=service.previsualiserAssistant(c,etape,"CARTE",perimetre);
        assertThat(apercu.lignes().get(0).candidats()).extracting(a -> a.cle()).containsExactly("CREATION");
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).isEmpty();
        var demande=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Confirmation(etape,perimetre,"CARTE",true,List.of(choisir(apercu,0,false)));
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE)).hasSize(1);
    }

    void tiersAssistant(Long id,String nom,String role,boolean autorise,String tva) {
        when(tiers.listerTiersActifs(c)).thenReturn(List.of(new TiersTresorerieDto(id,Set.of(role),nom,null,null,nom,true,autorise,null,null,null,null,tva)));
    }
    fr.wavy.tresorerie.dto.AssistantRapprochementDto.Apercu apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape e) {
        return service.previsualiserAssistant(c,e,"CARTE",perimetre());
    }
    fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix choisir(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Apercu a,int i,boolean nature) {
        var l=a.lignes().get(0);var p=l.candidats().get(i);
        return new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(l.operation().id(),p.cle(),p.pieceId(),p.tiersId(),p.montantPropose(),nature,true,true);
    }
    fr.wavy.tresorerie.dto.AssistantRapprochementDto.Confirmation demandeAssistant(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape e,fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix... choix) {
        return new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Confirmation(e,perimetre(),"CARTE",true,List.of(choix));
    }
    void impotAutorise() {
        tiersAssistant(59L,"IMPOT SUR LE REVENU","ORGANISME_FISCAL",true,null);
        jdbc.update("update operation_bancaire set tiers_id=59,tiers_reconnu='IMPOT SUR LE REVENU',type_tiers_reconnu='ADMINISTRATION',nature_operation='IMPOT',statut_classification='CLASSEE_AUTO',type_operation='PRELEVEMENT',libelle='IMPOT SUR LE REVENU' where id=?",op);
    }
    @Test void assistantAdobePrevisualiseSansEcrireEtConfirmationSepareNatureEtTiers() {
        tiersAssistant(7L,"Adobe Systems Software Ireland Ltd","FOURNISSEUR_GENERAL",false,null);
        jdbc.update("update operation_bancaire set tiers_id=null,tiers_reconnu=null,type_tiers_reconnu=null,nature_operation='SALAIRE',statut_classification='CLASSEE_AUTO',type_operation='PAIEMENT_CB',libelle='Paiement Adobe.com 31/01' where id=?",op);
        var avant=operations.findById(op).orElseThrow().getDateModification();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS);
        assertThat(a.propositions()).isEqualTo(1);assertThat(a.lignes().get(0).candidats().get(0).natureProposee()).isEqualTo(NatureOperationBancaire.FACTURE_FOURNISSEUR);
        assertThat(operations.findById(op).orElseThrow().getDateModification()).isEqualTo(avant);
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        var choix=choisir(a,0,false);assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choix)).reussis()).isEqualTo(1);
        assertThat(operations.findById(op).orElseThrow().getNatureOperation()).isEqualTo(NatureOperationBancaire.SALAIRE);
        a=apercu(a.etape());assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choisir(a,0,true))).reussis()).isEqualTo(1);
        assertThat(operations.findById(op).orElseThrow().getNatureOperation()).isEqualTo(NatureOperationBancaire.FACTURE_FOURNISSEUR);
    }
    @Test void assistantRespecteNatureManuelleEtFraisOpenAI() {
        tiersAssistant(7L,"OpenAI OpCo LLC","FOURNISSEUR_GENERAL",false,null);
        jdbc.update("update operation_bancaire set libelle='Frais carte étranger OPENAI CHATGPT',nature_operation='FRAIS_BANCAIRES',tiers_id=null where id=?",op);
        assertThat(apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS).propositions()).isZero();
        jdbc.update("update operation_bancaire set libelle='PAIEMENT OPENAI CHATGPT',statut_classification='CLASSEE_MANUELLE',nature_operation='APPORT_CAPITAL' where id=?",op);
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS);assertThat(a.lignes().get(0).candidats().get(0).natureProposee()).isNull();
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choisir(a,0,true))).refuses()).isEqualTo(1);
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
    }
    @Test void assistantImpotsAutorisesCreationLotEtDoubleExecutionSansDoublon() {
        impotAutorise();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);
        assertThat(a.eligibles()).isEqualTo(1);assertThat(a.lignes().get(0).candidats().get(0).typePiece()).isEqualTo("IMPOT_TAXE");
        assertThat(jdbc.queryForObject("select count(*) from piece_rapprochement where tenant_id=2903",Integer.class)).isZero();
        var demande=demandeAssistant(a.etape(),choisir(a,0,false));var resultat=service.confirmerAssistant(c,demande);
        assertThat(resultat.reussis()).isEqualTo(1);assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from piece_rapprochement where tenant_id=2903",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from rapprochement_bancaire where tenant_id=2903 and statut='VALIDE'",Integer.class)).isEqualTo(1);
        verify(factures,never()).creerFactureAchatDepuisOperationBancaire(any(),any());
    }
    @Test void assistantBenomeNonAutoriseEtFactureExistanteEtTvaInconnueSontExclus() {
        tiersAssistant(7L,"Benome","FOURNISSEUR_SERVICES",false,null);
        assertThat(apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES).eligibles()).isZero();
        tiersAssistant(7L,"Benome","FOURNISSEUR_SERVICES",true,"EXONERATION");jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS' where id=?",op);
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);assertThat(a.eligibles()).isZero();assertThat(a.lignes().get(0).anomalies()).anyMatch(m -> m.contains("existante"));
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of());tiersAssistant(7L,"Benome","FOURNISSEUR_SERVICES",true,null);
        a=apercu(a.etape());assertThat(a.eligibles()).isZero();assertThat(a.lignes().get(0).anomalies()).anyMatch(m -> m.contains("TVA"));
        verify(factures,never()).creerFactureAchatDepuisOperationBancaire(any(),any());
    }
    @Test void assistantCreationDistanteReponsePerdueGardeIntentionEtReprend() {
        tiersAssistant(7L,"Sans TVA","FOURNISSEUR_GENERAL",true,"EXONERATION");jdbc.update("update operation_bancaire set type_operation='VIREMENT_EMIS',statut_classification='CLASSEE_AUTO' where id=?",op);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of());
        var perte=new AtomicBoolean(true);var references=new HashSet<Long>();
        when(factures.creerFactureAchatDepuisOperationBancaire(any(),any())).thenAnswer(i -> {
            CreationFactureAchatDepuisOperationBancaireDemande d=i.getArgument(1);references.add(d.operationBancaireId());
            if (perte.getAndSet(false)) throw new IllegalStateException("Réponse perdue après création");
            return new FactureAchatRapprochementReponse(700L,"FA-700");
        });
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);var demande=demandeAssistant(a.etape(),choisir(a,0,false));
        assertThat(service.confirmerAssistant(c,demande).aReprendre()).isEqualTo(1);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.PROPOSE)).hasSize(1).allMatch(RapprochementBancaire::isPaiementEngage);
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(1);assertThat(references).containsExactly(op);
        assertThat(rapprochements.findByOperationBancaireIdAndStatut(op,StatutRapprochement.VALIDE)).hasSize(1);
    }
    @Test void assistantFiltresSocieteEtDoublonsRevalidesAvantEcriture() {
        impotAutorise();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);var choix=choisir(a,0,false);
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choix,choix)).conflits()).isEqualTo(2);
        var filtre=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre("AUTRE TIERS",null,null,null,null,null);
        assertThat(service.confirmerAssistant(c,new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Confirmation(a.etape(),filtre,"CARTE",true,List.of(choix))).refuses()).isEqualTo(1);
        var autre=new ContexteRequete(c.tenantId(),c.utilisateurId(),999L,c.roles(),null);
        assertThat(service.confirmerAssistant(autre,demandeAssistant(a.etape(),choix)).refuses()).isEqualTo(1);
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isFalse();
    }
    @Test void assistantLotPartielEtAutorisationChangeeApresApercu() {
        impotAutorise();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);var premier=choisir(a,0,false);
        Long autre=deuxiemeCarte();
        var second=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(autre,"CREATION",null,999L,new BigDecimal("100.00"),false,true,true);
        var r=service.confirmerAssistant(c,demandeAssistant(a.etape(),premier,second));assertThat(r.reussis()).isEqualTo(1);assertThat(r.refuses()).isEqualTo(1);
        assertThat(operations.findById(autre).orElseThrow().isRapprochee()).isFalse();
    }
    @Test void assistantFournisseurPartielEtPiecesMultiplesSansPaiementEnPrevisualisation() {
        tiersAssistant(7L,"Fournisseur","FOURNISSEUR_GENERAL",false,null);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of(
            new FactureOuverteRapprochementDto(10L,"ACHAT",7L,"FA-10",LocalDate.of(2026,2,1),null,new BigDecimal("40.00"),"A","Fournisseur"),
            new FactureOuverteRapprochementDto(11L,"ACHAT",7L,"FA-11",LocalDate.of(2026,2,1),null,new BigDecimal("60.00"),"B","Fournisseur")));
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.FOURNISSEURS);assertThat(a.propositions()).isEqualTo(2);
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choisir(a,0,false),choisir(a,1,false))).reussis()).isEqualTo(2);
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isTrue();
    }
    @Test void assistantSalairePeriodeBeneficiaireEtConfirmationIndividuelleAvecRecu() {
        tiersAssistant(7L,"Salarié","SALARIE",false,null);
        jdbc.update("update operation_bancaire set type_tiers_reconnu='SALARIE',nature_operation='SALAIRE',type_operation='VIREMENT_EMIS',libelle='Virement salaire' where id=?",op);
        when(factures.listerSalairesOuverts(eq(c),eq(7L),eq(2026),eq(1),isNull())).thenReturn(List.of(facture(88L)));
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.SALAIRES);assertThat(a.propositions()).isEqualTo(1);
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choisir(a,0,false))).reussis()).isEqualTo(1);
        verify(factures).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.SALAIRE),eq(88L),argThat(d -> d.rapprochementId()!=null && d.operationBancaireId().equals(op) && d.tiersId().equals(7L)));
    }
    @Test void assistantClientPaiementsMultiplesAvecReferencesIdempotentes() {
        tiersAssistant(7L,"Client","CLIENT",false,null);
        jdbc.update("update operation_bancaire set sens='CREDIT',debit=0,credit=100,type_tiers_reconnu='CLIENT',nature_operation='FACTURE_CLIENT' where id=?",op);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.VENTE,7L)).thenReturn(List.of(
            new FactureOuverteRapprochementDto(31L,"VENTE",7L,"FV-31",LocalDate.of(2026,2,1),null,new BigDecimal("40.00"),"A","Client"),
            new FactureOuverteRapprochementDto(32L,"VENTE",7L,"FV-32",LocalDate.of(2026,2,1),null,new BigDecimal("60.00"),"B","Client")));
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.CLIENTS);var demande=demandeAssistant(a.etape(),choisir(a,0,false),choisir(a,1,false));
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(2);
        assertThat(service.confirmerAssistant(c,demande).reussis()).isEqualTo(2);
        verify(factures,times(2)).enregistrerPaiement(eq(c),eq(TypeFactureRapprochement.VENTE),anyLong(),argThat(d -> d.rapprochementId()!=null));
    }

    @Test void formulaireNePeutPasInventerRapprochementValide() {
        var o=operations.findById(op).orElseThrow();
        var demande=new OperationBancaireDemande(o.getCompteBancaire().getId(),null,o.getDateOperation(),null,o.getLibelle(),null,
            o.getDebit(),BigDecimal.ZERO,o.getMontant(),o.getSens(),o.getTypeOperation(),o.getTiersReconnu(),o.getTypeTiersReconnu(),o.getNatureOperation(),o.getStatutClassification(),StatutOperationBancaire.RAPPROCHEE,o.getTiersId(),null,null);
        assertThatThrownBy(() -> service.modifierOperation(c,op,demande)).hasMessageContaining("service de rapprochement");
        assertThat(operations.findById(op).orElseThrow().isRapprochee()).isFalse();verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void assistantNeCreePasSiAutorisationOuNatureChangeApresApercu() {
        impotAutorise();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);var d=demandeAssistant(a.etape(),choisir(a,0,false));
        tiersAssistant(59L,"IMPOT SUR LE REVENU","ORGANISME_FISCAL",false,null);
        assertThat(service.confirmerAssistant(c,d).refuses()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from piece_rapprochement where tenant_id=2903",Integer.class)).isZero();
    }
    @Test void assistantRepriseApresFiltreNonRapprocheesNeRepayePas() {
        impotAutorise();var p=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre(null,null,null,null,StatutOperationBancaire.NON_RAPPROCHEE,null);
        var a=service.previsualiserAssistant(c,fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES,"CARTE",p);
        var d=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Confirmation(a.etape(),p,"CARTE",true,List.of(choisir(a,0,false)));
        var premier=service.confirmerAssistant(c,d);assertThat(premier.reussis()).isEqualTo(1);
        assertThat(service.confirmerAssistant(c,d).lignes().get(0).rapprochementId()).isEqualTo(premier.lignes().get(0).rapprochementId());
    }
    @Test void assistantPageVideNeDevientPasPerimetreGlobalEtPasDeLotClientSalaire() {
        impotAutorise();var p=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Perimetre(null,null,null,null,null,List.of());
        assertThat(service.previsualiserAssistant(c,fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES,"CARTE",p).operationsPerimetre()).isZero();
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);var choix=choisir(a,0,false);Long autre=deuxiemeCarte();
        var second=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(autre,"P:SALAIRE:12",12L,59L,new BigDecimal("100"),false,true,true);
        assertThatThrownBy(() -> service.confirmerAssistant(c,demandeAssistant(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.SALAIRES,choix,second))).hasMessageContaining("par opération");
    }
    @Test void assistantFraisBancairesSansInformationTvaRestentAVerifier() {
        tiersAssistant(7L,"Banque synthétique","FOURNISSEUR_GENERAL",true,null);
        when(factures.listerFacturesOuvertes(c,TypeFactureRapprochement.ACHAT,7L)).thenReturn(List.of());
        jdbc.update("update operation_bancaire set type_operation='FRAIS_BANCAIRES',nature_operation='FRAIS_BANCAIRES',statut_classification='CLASSEE_AUTO' where id=?",op);
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.PIECES);assertThat(a.eligibles()).isZero();
        tiersAssistant(7L,"Banque synthétique","FOURNISSEUR_GENERAL",true,"EXONERATION");assertThat(apercu(a.etape()).eligibles()).isEqualTo(1);
    }

    RegleRapprochementFournisseurFacturesDto regleBenome() {
        return new RegleRapprochementFournisseurFacturesDto(1L,c.tenantId(),c.societeCouranteId(),7L,"Benome","BENOME","OPERATION_BANCAIRE","CONTIENT",100,true);
    }
    void historiqueBenome() {
        when(tiers.listerTiersActifs(c)).thenReturn(List.of(new TiersTresorerieDto(7L,Set.of("PRESTATAIRE"),"Benome",null,null,null,true,false),
                new TiersTresorerieDto(59L,Set.of("SALARIE"),null,"Sira","Benoit",null,true,false)));
        when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(regleBenome()));
        jdbc.update("update operation_bancaire set tiers_id=null,tiers_reconnu=null,type_tiers_reconnu=null,libelle='Virement émis - VIR INST vers Benome Benoit Sira - wavy services Fac83 nov25 B.Sira',nature_operation='SALAIRE',statut_classification='CLASSEE_MANUELLE',date_operation='2026-01-29' where id=?",op);
    }
    @Test void identificationHistoriqueLitLaRegleSansEcrirePuisConfirmeLeLotSansPaiement() {
        historiqueBenome();var avant=operations.findById(op).orElseThrow().getDateModification();
        var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS);
        assertThat(a.lignes().get(0).candidats()).hasSize(1);
        var certaine=a.lignes().get(0).candidats().stream().filter(x -> x.sur()).findFirst().orElseThrow();
        assertThat(certaine.tiersId()).isEqualTo(7L);assertThat(certaine.cle()).isEqualTo("REGLE_BANCAIRE:7");
        assertThat(operations.findById(op).orElseThrow().getDateModification()).isEqualTo(avant);
        assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
        var choix=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(op,certaine.cle(),null,7L,BigDecimal.ZERO,false,false,false);
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choix)).reussis()).isEqualTo(1);
        var apres=operations.findById(op).orElseThrow();assertThat(apres.getTiersId()).isEqualTo(7L);
        assertThat(apres.getNatureOperation()).isEqualTo(NatureOperationBancaire.SALAIRE);assertThat(apres.getStatutClassification()).isEqualTo(StatutClassificationOperation.CLASSEE_MANUELLE);
        assertThat(apres.getStatutRapprochement()).isEqualTo(StatutOperationBancaire.NON_RAPPROCHEE);assertThat(apres.isRapprochee()).isFalse();
        verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
    }
    @Test void identificationRefuseUnConflitApparuApresLaPrevisualisation() {
        historiqueBenome();var a=apercu(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS);
        var choix=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(op,"REGLE_BANCAIRE:7",null,7L,BigDecimal.ZERO,false,false,false);
        when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(regleBenome(),
                new RegleRapprochementFournisseurFacturesDto(2L,c.tenantId(),c.societeCouranteId(),59L,"Sira","SIRA","OPERATION_BANCAIRE","CONTIENT",1000,true)));
        assertThat(apercu(a.etape()).lignes().get(0).candidats()).noneMatch(x -> x.sur());
        assertThat(service.confirmerAssistant(c,demandeAssistant(a.etape(),choix)).reussis()).isZero();assertThat(operations.findById(op).orElseThrow().getTiersId()).isNull();
    }
    @Test void identificationNecraseJamaisUnTiersDejaRenseigne() {
        historiqueBenome();jdbc.update("update operation_bancaire set tiers_id=59,tiers_reconnu='Benoit Sira' where id=?",op);
        var choix=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(op,"REGLE_BANCAIRE:7",null,7L,BigDecimal.ZERO,false,false,false);
        assertThat(service.confirmerAssistant(c,demandeAssistant(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS,choix)).reussis()).isZero();assertThat(operations.findById(op).orElseThrow().getTiersId()).isEqualTo(59L);
    }
    @Test void identificationVerrouilleEtRelitAvantDEcraserUneIdentificationConcurrente() {
        historiqueBenome();var armee=new AtomicBoolean(true);
        when(factures.listerReglesRapprochementFournisseur(c)).thenAnswer(i->{if(armee.getAndSet(false))jdbc.update("update operation_bancaire set tiers_id=59,tiers_reconnu='Benoit Sira' where id=?",op);return List.of(regleBenome());});
        var choix=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(op,"REGLE_BANCAIRE:7",null,7L,BigDecimal.ZERO,false,false,false);
        assertThat(service.confirmerAssistant(c,demandeAssistant(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS,choix)).reussis()).isZero();assertThat(operations.findById(op).orElseThrow().getTiersId()).isEqualTo(59L);
    }
    @Test void identificationGroupeeRevalideChaqueOperationEtRespecteLePerimetre() {
        historiqueBenome();Long premiere=op;Long seconde=deuxiemeCarte();
        jdbc.update("update operation_bancaire set tiers_id=null,tiers_reconnu=null,libelle='VIR BENOME',nature_operation='SALAIRE',statut_classification='CLASSEE_MANUELLE' where id in (?,?)",premiere,seconde);
        var a=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(premiere,"REGLE_BANCAIRE:7",null,7L,BigDecimal.ZERO,false,false,false);
        var b=new fr.wavy.tresorerie.dto.AssistantRapprochementDto.Choix(seconde,"REGLE_BANCAIRE:7",null,7L,BigDecimal.ZERO,false,false,false);
        assertThat(service.confirmerAssistant(c,demandeAssistant(fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS,a,b)).reussis()).isEqualTo(2);
        assertThat(operations.findById(premiere).orElseThrow().getStatutRapprochement()).isEqualTo(StatutOperationBancaire.NON_RAPPROCHEE);
        var autre=new ContexteRequete(c.tenantId()+1,c.utilisateurId(),c.societeCouranteId(),List.of("ADMIN_TENANT"),null);
        assertThat(service.previsualiserAssistant(autre,fr.wavy.tresorerie.dto.AssistantRapprochementDto.Etape.TIERS,"CARTE",perimetre()).lignes()).isEmpty();
    }

    @Test void identificationDuSalarieSurUnVirementDeFraisDansSonImport() {
        String libelle = "Virement émis - VIR INST vers JEAN MARIE NIENGEL frais Rouen JMNiengele dec25";
        Long compte = operations.findById(op).orElseThrow().getCompteBancaire().getId();
        var salarie = new TiersTresorerieDto(4L, Set.of("SALARIE", "PORTEUR_CARTE"), null,
                "Niengele", "Jean-Marie", null, true, false);
        var concurrent = new TiersTresorerieDto(5L, Set.of("AUTRE"), "Autre bénéficiaire", null, null, null, true, false);
        var regle = new RegleRapprochementFournisseurFacturesDto(1L, c.tenantId(), c.societeCouranteId(),
                4L, "Alias bancaire", "JEAN MARIE NIENGEL", "OPERATION_BANCAIRE", "CONTIENT", 100, true);
        var inactive = new RegleRapprochementFournisseurFacturesDto(2L, c.tenantId(), c.societeCouranteId(),
                4L, "Alias bancaire", "JEAN MARIE NIENGEL", "OPERATION_BANCAIRE", "CONTIENT", 100, false);
        var autreSociete = new RegleRapprochementFournisseurFacturesDto(3L, c.tenantId(), c.societeCouranteId()+1,
                5L, "Autre société", "NIENGEL", "OPERATION_BANCAIRE", "CONTIENT", 300, true);
        var autreTenant = new RegleRapprochementFournisseurFacturesDto(4L, c.tenantId()+1, c.societeCouranteId(),
                5L, "Autre tenant", "NIENGEL", "OPERATION_BANCAIRE", "CONTIENT", 300, true);
        var regleConcurrente = new RegleRapprochementFournisseurFacturesDto(5L, c.tenantId(), c.societeCouranteId(),
                5L, "Règle concurrente", "FRAIS ROUEN", "OPERATION_BANCAIRE", "CONTIENT", 300, true);
        when(tiers.listerTiersActifs(c)).thenReturn(List.of(salarie, concurrent));
        when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(inactive, autreSociete, autreTenant));
        String csv = "Date d'opération;Libellé;Débit euros;Crédit euros\n15/01/2026;" + libelle + ";270,27;\n";
        var fichier = new org.springframework.mock.web.MockMultipartFile("fichier", "frais.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            long importId = importBancaire.importer(c, compte, fichier).importId();
            var scope = new Perimetre(null,null,null,null,null,null,importId,null,true);
            var avant = service.previsualiserAssistant(c, Etape.TIERS, "CARTE", scope).lignes().get(0);
            assertThat(avant.operation().tiersId()).isNull();
            assertThat(avant.candidats()).isEmpty();
            long id = avant.operation().id();
            assertThat(avant.operation().dateOperation()).isEqualTo(LocalDate.of(2026, 1, 15));

            when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(regle, regleConcurrente));
            var conflit = service.previsualiserAssistant(c, Etape.TIERS, "CARTE", scope).lignes().get(0);
            assertThat(conflit.candidats()).extracting(Candidat::tiersId).containsExactlyInAnyOrder(4L, 5L);
            assertThat(conflit.candidats()).noneMatch(Candidat::sur);

            when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(regle, autreSociete, autreTenant));
            var apercu = service.previsualiserAssistant(c, Etape.TIERS, "CARTE", scope);
            var individuel = new Perimetre(null,null,null,null,null,List.of(id),importId,null,true);
            var propositions = apercu.lignes().get(0).candidats();
            assertThat(propositions).singleElement().satisfies(candidat -> {
                assertThat(candidat.cle()).isEqualTo("REGLE_BANCAIRE:4");
                assertThat(candidat.nomTiers()).contains("Jean-Marie", "Niengele");
                assertThat(candidat.typeTiers()).isEqualTo(TypeTiersReconnu.SALARIE);
                assertThat(candidat.sur()).isTrue();
            });
            assertThat(service.previsualiserAssistant(c, Etape.TIERS, "CARTE", individuel).lignes().get(0).candidats())
                    .containsExactlyElementsOf(propositions);
            assertThat(service.previsualiserAssistant(c, Etape.TIERS, "CARTE",
                    new Perimetre(null,null,null,null,null,List.of(id),importId+1,null,true)).lignes()).isEmpty();
            assertThat(apercu.lignes().get(0).operation().pieceRapprochementAutorisee()).isFalse();
            assertThat(operations.findById(id).orElseThrow().getTiersId()).isNull();

            var demande = new Confirmation(Etape.TIERS, scope, "CARTE", true, List.of(choisir(apercu,0,false)));
            assertThat(service.confirmerAssistant(c, demande).reussis()).isEqualTo(1);
            var apres = operations.findById(id).orElseThrow();
            assertThat(apres.getTiersId()).isEqualTo(4L);
            assertThat(apres.getMontant()).isEqualByComparingTo(avant.operation().montant());
            assertThat(apres.getNatureOperation()).isEqualTo(avant.operation().natureOperation());
            assertThat(apres.getStatutRapprochement()).isEqualTo(avant.operation().statutRapprochement());
            assertThat(service.previsualiserAssistant(c, Etape.TIERS, "CARTE", scope).lignes()).isEmpty();
            assertThat(rapprochements.findByOperationBancaireIdAndStatut(id, StatutRapprochement.VALIDE)).isEmpty();
            verify(factures, never()).enregistrerPaiement(any(), any(), anyLong(), any());
        } finally {
            jdbc.update("delete from operation_bancaire where compte_bancaire_id=? and import_releve_bancaire_id is not null",compte);
            jdbc.update("delete from import_releve_bancaire where compte_bancaire_id=?",compte);
        }
    }

    @Test void lotCertainSurDeuxImportsEcarteCasdenEtBredEtActualiseLesCompteurs() {
        Long compte = operations.findById(op).orElseThrow().getCompteBancaire().getId();
        var salarie = new TiersTresorerieDto(4L, Set.of("SALARIE", "PORTEUR_CARTE"), null,
                "Niengele", "Jean-Marie", null, true, false);
        var ecole = new TiersTresorerieDto(6L, Set.of("CLIENT"), "École Supérieure de la Banque", null, null, null, true, false);
        var casden = new TiersTresorerieDto(8L, Set.of("BANQUE"), "CASDEN", null, null, null, true, false);
        var bred = new TiersTresorerieDto(9L, Set.of("BANQUE"), "BRED Banque Populaire", null, null, null, true, false);
        when(tiers.listerTiersActifs(c)).thenReturn(List.of(salarie, ecole, casden, bred));
        when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of());

        try {
            long premier = importerPourIdentification(compte, "salarie.csv",
                    "15/01/2026;Virement émis - VIR INST vers JEAN MARIE NIENGEL frais Rouen CASDEN;270,27;");
            long second = importerPourIdentification(compte, "ecole.csv",
                    "15/01/2026;Virement reçu ÉCOLE SUPÉRIEURE DE LA BANQUE BRED BANQUE POPULAIRE;;42,00");
            assertThat(premier).isNotEqualTo(second);
            var sansRegles = service.previsualiserAssistant(c, Etape.TIERS, "CARTE", consultation());
            assertThat(sansRegles.lignes().stream().flatMap(l -> l.candidats().stream()).map(Candidat::tiersId))
                    .contains(8L, 9L);
            when(factures.listerReglesRapprochementFournisseur(c)).thenReturn(List.of(
                    reglePourIdentification(101L, 4L, "JEAN MARIE NIENGEL"),
                    reglePourIdentification(102L, 4L, "NIENGEL FRAIS ROUEN"),
                    reglePourIdentification(103L, 6L, "ÉCOLE SUPÉRIEURE DE LA BANQUE")));

            var apercu = service.previsualiserAssistant(c, Etape.TIERS, "CARTE", consultation());
            assertThat(apercu.lignes()).hasSize(2).allSatisfy(l ->
                    assertThat(l.candidats()).singleElement().matches(Candidat::sur));
            assertThat(apercu.lignes().stream().map(l -> l.candidats().get(0).tiersId()))
                    .containsExactlyInAnyOrder(4L, 6L);
            var choix = apercu.lignes().stream().map(l -> {
                var candidat = l.candidats().get(0);
                return new Choix(l.operation().id(), candidat.cle(), null, candidat.tiersId(), BigDecimal.ZERO, false, false, false);
            }).toList();
            var demande = new Confirmation(Etape.TIERS, consultation(), "CARTE", true, choix);
            assertThat(service.compterOperations(c, consultation()).sansTiers()).isEqualTo(2);
            var etranger = new ContexteRequete(c.tenantId(), c.utilisateurId(), c.societeCouranteId()+1, List.of("ADMIN_TENANT"), null);
            assertThat(service.confirmerAssistant(etranger, demande).reussis()).isZero();
            assertThat(service.confirmerAssistant(c, demande).reussis()).isEqualTo(2);
            assertThat(service.compterOperations(c, consultation()).sansTiers()).isZero();
            assertThat(service.compterOperations(c, consultation()).tiersIdentifies()).isEqualTo(3);
            assertThat(service.previsualiserAssistant(c, Etape.TIERS, "CARTE", consultation()).lignes()).isEmpty();
            assertThat(operations.findById(op).orElseThrow().getTiersId()).isEqualTo(7L);
            assertThat(service.confirmerAssistant(c, demande).reussis()).isZero();
            verify(factures, never()).enregistrerPaiement(any(), any(), anyLong(), any());
        } finally {
            jdbc.update("delete from operation_bancaire where compte_bancaire_id=? and import_releve_bancaire_id is not null",compte);
            jdbc.update("delete from import_releve_bancaire where compte_bancaire_id=?",compte);
        }
    }

    private long importerPourIdentification(Long compte, String nom, String ligneCsv) {
        String csv = "Date d'opération;Libellé;Débit euros;Crédit euros\n" + ligneCsv + "\n";
        var fichier = new org.springframework.mock.web.MockMultipartFile("fichier", nom, "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return importBancaire.importer(c, compte, fichier).importId();
    }

    private RegleRapprochementFournisseurFacturesDto reglePourIdentification(Long id, Long tiersId, String motif) {
        return new RegleRapprochementFournisseurFacturesDto(id, c.tenantId(), c.societeCouranteId(), tiersId,
                motif, motif, "OPERATION_BANCAIRE", "CONTIENT", 100, true);
    }

    @Test void identificationBenomeDepuisUnNouveauFichierImporte() {
        historiqueBenome();Long compte=operations.findById(op).orElseThrow().getCompteBancaire().getId();
        String csv="Date d'opération;Libellé;Débit euros;Crédit euros\n29/01/2026;Virement émis - VIR INST vers Benome Benoit Sira - wavy services Fac83 nov25 B.Sira;10140,00;\n";
        try {
            var fichier=new org.springframework.mock.web.MockMultipartFile("fichier","benome.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(importBancaire.importer(c,compte,fichier).nombreLignesImportees()).isEqualTo(1);
            var nouvelle=operations.findByTenantIdAndSocieteInterneIdAndCompteBancaireId(c.tenantId(),c.societeCouranteId(),compte).stream()
                    .filter(o -> !o.getId().equals(op)).findFirst().orElseThrow();
            assertThat(nouvelle.getTiersId()).isEqualTo(7L);assertThat(nouvelle.getTiersReconnu()).isEqualTo("Benome");
            assertThat(nouvelle.getStatutRapprochement()).isEqualTo(StatutOperationBancaire.NON_RAPPROCHEE);assertThat(nouvelle.isRapprochee()).isFalse();
            verify(factures,never()).enregistrerPaiement(any(),any(),anyLong(),any());
        } finally {
            jdbc.update("delete from operation_bancaire where compte_bancaire_id=? and import_releve_bancaire_id is not null",compte);
            jdbc.update("delete from import_releve_bancaire where compte_bancaire_id=?",compte);
        }
    }

}
