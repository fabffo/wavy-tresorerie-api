package fr.wavy.tresorerie.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import fr.wavy.tresorerie.client.TiersTresorerieClient;
import fr.wavy.tresorerie.client.FacturesTresorerieClient;
import fr.wavy.tresorerie.dto.AssistantRapprochementDto.*;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.OperationBancaireDemande;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;
import fr.wavy.tresorerie.modele.*;
import fr.wavy.tresorerie.repository.*;
import fr.wavy.tresorerie.service.importreleve.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="wavy.import.test.jdbc-url", matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(properties={"spring.datasource.url=${wavy.import.test.jdbc-url}",
        "logging.level.org.hibernate.SQL=WARN", "logging.level.root=WARN", "spring.datasource.username=${user.name}", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
class ImportReleveBancaireIntegrationTest {
    @Autowired ImportReleveBancaireService service;
    @Autowired TresorerieService tresorerie;
    @Autowired CompteBancaireRepository comptes;
    @Autowired OperationBancaireRepository operations;
    @Autowired ImportReleveBancaireRepository imports;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TiersTresorerieClient tiers;
    @MockitoBean FacturesTresorerieClient factures;
    final ContexteRequete contexte = new ContexteRequete(7101L,7102L,7103L,List.of("ADMIN_TENANT"),null);
    Long compteId;
    final List<Long> comptesCrees = new ArrayList<>();
    final String entetes="Date d'opération;Libellé;Débit euros;Crédit euros\n";
    final String linkedin="12/01/2026;LinkedIn;168,59;\n";

    @BeforeEach void preparer() {
        compteId=comptes.saveAndFlush(nouveauCompte()).getId();
        comptesCrees.add(compteId);
        when(tiers.listerSalaries(any())).thenReturn(List.of());
    }
    CompteBancaire nouveauCompte() {
        CompteBancaire compte=new CompteBancaire();
        compte.setTenantId(contexte.tenantId());compte.setSocieteInterneId(contexte.societeCouranteId());
        compte.setNomCompte("Test import");compte.setBanque("Test");compte.setIban(UUID.randomUUID().toString().substring(0,30));
        return compte;
    }
    @AfterEach void nettoyerLesFixtures() {
        for (Long id : comptesCrees) {
            jdbc.update("delete from rapprochement_bancaire where operation_bancaire_id in (select id from operation_bancaire where compte_bancaire_id=?)", id);
            jdbc.update("delete from operation_bancaire where compte_bancaire_id=?", id);
            jdbc.update("delete from import_releve_bancaire where compte_bancaire_id=?", id);
            jdbc.update("delete from compte_bancaire where id=?", id);
        }
    }
    MockMultipartFile csv(String nom,String texte) {
        return new MockMultipartFile("fichier",nom,"text/csv",texte.getBytes(StandardCharsets.UTF_8));
    }
    List<OperationBancaire> lignes() {
        return operations.findByTenantIdAndSocieteInterneIdAndCompteBancaireId(7101L,7103L,compteId);
    }
    @Test void consultationFiltreLImportEtCompteLesSoldesSurToutesLesPages() {
        long ancien=service.importer(contexte,compteId,csv("ancien.csv",entetes+linkedin)).importId();
        long dernier=service.importer(contexte,compteId,csv("dernier.csv",entetes
                +"01/01/2026;Aucun tiers;10,00;\n02/01/2026;Identifié;20,00;\n"
                +"03/01/2026;Partiel;30,00;\n04/01/2026;Rapproché;40,00;\n")).importId();
        assertThat(tresorerie.listerImports(contexte).get(0).id()).isEqualTo(dernier);

        for (var ligne : lignes()) {
            switch (ligne.getLibelle()) {
                case "Identifié" -> jdbc.update("update operation_bancaire set tiers_id=7 where id=?", ligne.getId());
                case "Partiel", "Rapproché" -> jdbc.update("""
                        insert into rapprochement_bancaire (tenant_id,societe_interne_id,operation_bancaire_id,montant_rapproche,statut,document_associe_numero)
                        values (7101,7103,?,?,'VALIDE','FAC-123')
                        """, ligne.getId(), ligne.getLibelle().equals("Partiel") ? 15 : 40);
            }
        }

        var filtres=new Perimetre(null,null,null,null,null,null,dernier,null,true);
        assertThat(tresorerie.compterOperations(contexte,filtres)).isEqualTo(new Compteurs(4,1,1,1,1,3));
        assertThat(tresorerie.compterOperations(contexte,new Perimetre(null,null,null,null,null,null,ancien,null,true)).total()).isEqualTo(1);
        assertThat(tresorerie.compterOperations(contexte,new Perimetre(null,null,null,null,null,null,null,null,true)).total()).isEqualTo(5);
        var etranger=new ContexteRequete(contexte.tenantId(),contexte.utilisateurId(),9999L,List.of("ADMIN_TENANT"),null);
        assertThat(tresorerie.compterOperations(etranger,filtres).total()).isZero();
        var page=tresorerie.listerOperations(contexte,null,null,null,null,null,null,null,null,null,null,
                null,null,null,dernier,"PARTIELLE",0,1,"statutConsultation","ASC",null);
        var operation=page.getContent().get(0);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(operation.numeroPieceAssociee()).isEqualTo("FAC-123");
        assertThat(operation.montantRapproche()).isEqualByComparingTo("15");
        assertThat(operation.statutRapprochement()).isEqualTo(StatutOperationBancaire.NON_RAPPROCHEE);
        var detail=tresorerie.consulterOperation(contexte,operation.id());
        assertThat(detail.solde()).isEqualByComparingTo("15");
        assertThat(detail.historique()).hasSize(1);
        assertThatThrownBy(() -> tresorerie.previsualiserAssistant(contexte,Etape.SALAIRES,"CARTE",filtres))
                .hasMessageContaining("3 opérations restent à identifier");
    }

    @Test void consultationAfficheLesRolesActifsDuTiersAssocie() {
        long importId = service.importer(contexte, compteId, csv("roles.csv", entetes
                + "01/01/2026;Multirôles;10,00;\n02/01/2026;Tiers inactif;20,00;\n"
                + "03/01/2026;Sans tiers;30,00;\n04/01/2026;Tiers absent;40,00;\n")).importId();
        jdbc.update("""
                update operation_bancaire set tiers_id = case libelle
                    when 'Multirôles' then 7 when 'Tiers inactif' then 8 when 'Tiers absent' then 9 end,
                    type_tiers_reconnu = 'CLIENT'
                where import_releve_bancaire_id = ?
                """, importId);
        when(tiers.listerTiersActifs(contexte)).thenReturn(List.of(
                new TiersTresorerieDto(7L, Set.of("SALARIE", "PORTEUR_CARTE"), null, "Nom", "Prénom", null, true, true),
                new TiersTresorerieDto(8L, Set.of("MARCHAND"), "Inactif", null, null, null, false, false)));
        clearInvocations(tiers);

        var page = tresorerie.listerOperations(contexte, null, null, null, null, null, null, null, null,
                null, null, null, null, null, importId, null, 0, 10, "dateOperation", "ASC", null);
        assertThat(page.getContent()).hasSize(4);
        assertThat(page.getContent().get(0).rolesTiers()).containsExactly("PORTEUR_CARTE", "SALARIE");
        assertThat(page.getContent().get(0).pieceRapprochementAutorisee()).isTrue();
        assertThat(page.getContent().subList(1, 4)).allSatisfy(operation -> assertThat(operation.rolesTiers()).isEmpty());
        verify(tiers).listerTiersActifs(contexte);
        verifyNoMoreInteractions(tiers);

        var autreSociete = new ContexteRequete(contexte.tenantId(), contexte.utilisateurId(), 9999L, List.of("ADMIN_TENANT"), null);
        assertThat(tresorerie.listerOperations(autreSociete)).isEmpty();
        verify(tiers).listerTiersActifs(autreSociete);
    }

    @Test void triEtFiltresRolesPartagentLePerimetreDeConsultation() {
        long importId = service.importer(contexte, compteId, csv("roles-tri.csv", entetes
                + "01/01/2026;Tiers multirôle;10,00;\n02/01/2026;Tiers client;20,00;\n"
                + "03/01/2026;Tiers marchand;30,00;\n04/01/2026;Tiers sans rôle;40,00;\n"
                + "05/01/2026;Sans tiers;50,00;\n")).importId();
        long autreImport = service.importer(contexte, compteId, csv("roles-annexe.csv", entetes
                + "06/01/2026;Tiers multirôle annexe;60,00;\n")).importId();
        jdbc.update("""
                update operation_bancaire set tiers_reconnu = libelle, tiers_id = case libelle
                    when 'Tiers multirôle' then 7 when 'Tiers client' then 8
                    when 'Tiers marchand' then 9 when 'Tiers sans rôle' then 10
                    when 'Tiers multirôle annexe' then 7 end,
                    type_operation = 'VIREMENT_EMIS'
                where import_releve_bancaire_id in (?, ?)
                """, importId, autreImport);
        when(tiers.listerTiersActifs(contexte)).thenReturn(List.of(
                new TiersTresorerieDto(7L, Set.of("SALARIE", "PORTEUR_CARTE"), "Multirôle", null, null, null, true, false),
                new TiersTresorerieDto(8L, Set.of("CLIENT"), "Client", null, null, null, true, false),
                new TiersTresorerieDto(9L, Set.of("MARCHAND"), "Marchand", null, null, null, true, false),
                new TiersTresorerieDto(10L, Set.of(), "Sans rôle", null, null, null, true, false),
                new TiersTresorerieDto(11L, Set.of("CANDIDAT"), "Inactif", null, null, null, false, false)));

        var premier = pageRoles(contexte, importId, null, null, null, 0, "ASC");
        var suivant = pageRoles(contexte, importId, null, null, null, 1, "ASC");
        assertThat(premier.getTotalElements()).isEqualTo(5);
        assertThat(premier.map(o -> o.libelle()).getContent()).containsExactly("Tiers client", "Tiers marchand");
        assertThat(suivant.map(o -> o.libelle()).getContent()).containsExactly("Tiers multirôle", "Sans tiers");
        assertThat(pageRoles(contexte, importId, null, null, null, 0, "DESC").map(o -> o.libelle()).getContent())
                .containsExactly("Tiers multirôle", "Tiers marchand");
        assertThat(suivant.getContent().get(0).libellesRolesTiers()).isEqualTo("Porteur de carte, Salarié");

        var roles = List.of("SALARIE", "MARCHAND");
        assertThat(pageRoles(contexte, importId, null, null, List.of("PORTEUR_CARTE"), 0, "ASC").getTotalElements()).isOne();
        assertThat(pageRoles(contexte, importId, null, null, roles, 0, "ASC").map(o -> o.libelle()).getContent())
                .containsExactly("Tiers marchand", "Tiers multirôle");
        var pageFiltree = pageRoles(contexte, importId, "Tiers multi", "TIERS_IDENTIFIE", roles, 0, "ASC");
        assertThat(pageFiltree.map(o -> o.libelle()).getContent()).containsExactly("Tiers multirôle");
        var perimetre = new Perimetre("Tiers multi", null, null, null, null, null, importId, "TIERS_IDENTIFIE", true, roles);
        var compteurs = tresorerie.compterOperations(contexte, perimetre);
        assertThat(compteurs.total()).isEqualTo(pageFiltree.getTotalElements());
        assertThat(compteurs.tiersIdentifies()).isOne();
        assertThat(compteurs.sansTiers()).isZero();
        assertThat(compteurs.rolesTiersDisponibles()).extracting(fr.wavy.tresorerie.dto.RoleTiersConsultation::code)
                .containsExactly("CLIENT", "MARCHAND", "PORTEUR_CARTE", "SALARIE");
        assertThat(tresorerie.compterOperations(contexte, new Perimetre(null, null, null, null, null, null, importId, null, true))
                .sansTiers()).isOne();
        for (var etape : List.of(Etape.TIERS, Etape.SALAIRES, Etape.CLIENTS, Etape.FOURNISSEURS)) {
            assertThat(tresorerie.previsualiserAssistant(contexte, etape, "CARTE", perimetre).operationsPerimetre()).isOne();
        }

        long id = pageFiltree.getContent().get(0).id();
        when(tiers.listerTiersActifs(contexte)).thenReturn(List.of(
                new TiersTresorerieDto(7L, Set.of("CLIENT"), "Ancien salarié", null, null, null, true, false)));
        var choix = new Choix(id, "P:SALAIRE:1", 1L, 7L, BigDecimal.TEN, false, false, false);
        var bilan = tresorerie.confirmerAssistant(contexte, new Confirmation(Etape.SALAIRES, perimetre, "CARTE", true, List.of(choix)));
        assertThat(bilan.lignes()).extracting(Resultat::code).containsExactly("HORS_PERIMETRE");

        for (var autre : List.of(
                new ContexteRequete(9999L, 7102L, 7103L, List.of("ADMIN_TENANT"), null),
                new ContexteRequete(7101L, 7102L, 9999L, List.of("ADMIN_TENANT"), null))) {
            assertThat(pageRoles(autre, importId, null, null, roles, 0, "ASC")).isEmpty();
            assertThat(tresorerie.compterOperations(autre, perimetre).total()).isZero();
        }
    }

    private org.springframework.data.domain.Page<fr.wavy.tresorerie.dto.OperationBancaireReponse> pageRoles(
            ContexteRequete c, long importId, String tiersReconnu, String statut, List<String> roles, int page, String direction) {
        return tresorerie.listerOperations(c, null, null, null, null, null, null, null, null, tiersReconnu,
                null, null, null, null, importId, statut, page, 2, "rolesTiers", direction, roles);
    }

    @Test void conserveLesOccurrencesEtReimporteSansDoublons() {
        String texte=entetes+linkedin+"13/01/2026;Autre;;10,00\n"+linkedin;
        assertThat(service.importer(contexte,compteId,csv("initial.csv",texte)).nombreLignesImportees()).isEqualTo(3);
        assertThat(service.importer(contexte,compteId,csv("initial.csv",texte)).nombreLignesImportees()).isZero();
        assertThat(service.importer(contexte,compteId,csv("renomme.csv",entetes+linkedin+linkedin+"13/01/2026;Autre;;10,000\n")).nombreLignesImportees()).isZero();
        assertThat(lignes()).hasSize(3).filteredOn(o -> o.getLibelle().equals("LinkedIn")).hasSize(2);
    }
    @Test void serialiseLesImportsConcurrents() throws Exception {
        String chemin=System.getProperty("wavy.import.test.xlsx");
        byte[] contenu=chemin==null ? (entetes+linkedin+linkedin).getBytes(StandardCharsets.UTF_8) : Files.readAllBytes(Path.of(chemin));
        String nom=chemin==null ? "test.csv" : "test.xlsx";
        int nombre=chemin==null ? 2 : 89;
        var pool=Executors.newFixedThreadPool(2);var depart=new CountDownLatch(1);
        try {
            Callable<Integer> travail=()->{depart.await();return service.importer(contexte,compteId,
                    new MockMultipartFile("fichier",nom,"application/octet-stream",contenu)).nombreLignesImportees();};
            var premier=pool.submit(travail);var second=pool.submit(travail);depart.countDown();
            assertThat(List.of(premier.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(nombre,0);
            assertThat(lignes()).hasSize(nombre);
        } finally {pool.shutdownNow();}
    }
    @Test void reconnaitLesAnciensHashesSansModifierLesRapprochements() throws Exception {
        for(String algorithme:List.of("MD5","SHA-256")) {
            var ligne=new LigneReleveBancaire(2,LocalDate.of(2026,1,12),null,"LinkedIn",null,new BigDecimal("168.59"),BigDecimal.ZERO,new BigDecimal("-168.59"));
            String ancien=algorithme.equals("SHA-256") ? service.hash(contexte,compteId,ligne) : HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("MD5").digest(("7101|7103|"+compteId+"|2026-01-12|LinkedIn|-168.59|").getBytes(StandardCharsets.UTF_8)));
            // Same payload as the historical SQL migration and Java SHA-256 implementation.
            ImportReleveBancaire historique=new ImportReleveBancaire();
            historique.setTenantId(7101L);historique.setSocieteInterneId(7103L);
            historique.setCompteBancaire(comptes.findById(compteId).orElseThrow());historique.setNomFichier("historique.csv");
            historique=imports.saveAndFlush(historique);
            OperationBancaire op=new OperationBancaire();op.setTenantId(7101L);op.setSocieteInterneId(7103L);
            op.setImportReleveBancaire(historique);
            op.setCompteBancaire(comptes.findById(compteId).orElseThrow());op.setDateOperation(ligne.dateOperation());
            op.setLibelle("Ancien libelle modifie");op.setMontant(ligne.montant());op.setDebit(ligne.debit());op.setSens(SensOperation.DEBIT);
            op.setHashOperation(ancien);op.setRapprochee(true);op.setStatutRapprochement(StatutOperationBancaire.RAPPROCHEE);
            op=operations.saveAndFlush(op);
            jdbc.update("insert into rapprochement_bancaire (tenant_id,societe_interne_id,operation_bancaire_id,montant_rapproche,statut) values (7101,7103,?,168.59,'VALIDE')",op.getId());
            assertThat(service.importer(contexte,compteId,csv("test.csv",entetes+linkedin+linkedin)).nombreLignesImportees()).isEqualTo(1);
            assertThat(service.importer(contexte,compteId,csv("bis.csv",entetes+linkedin+linkedin)).nombreLignesImportees()).isZero();
            var conservee=operations.findById(op.getId()).orElseThrow();
            assertThat(conservee.getHashOperation()).isEqualTo(ancien);assertThat(conservee.isRapprochee()).isTrue();
            assertThat(conservee.getImportReleveBancaire().getId()).isEqualTo(historique.getId());
            assertThat(jdbc.queryForObject("select count(*) from rapprochement_bancaire where operation_bancaire_id=?",Integer.class,op.getId())).isEqualTo(1);
            // A separate empty account for the other historical digest algorithm.
            preparer();
        }
    }
    @Test void annuleToutLImportEnCasDEchecPostgresql() {
        assertThatThrownBy(() -> service.importer(contexte,compteId,csv("invalide.csv",
                entetes+linkedin+"13/01/2026;"+"X".repeat(501)+";1,00;\n")))
                .isInstanceOf(RuntimeException.class);
        assertThat(lignes()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from import_releve_bancaire where compte_bancaire_id=?",
                Integer.class,compteId)).isZero();
    }

    @Test void conserveLIdentiteImporteeLorsDesModifications() {
        service.importer(contexte,compteId,csv("test.csv",entetes+linkedin+linkedin));
        var op=lignes().get(1);String hash=op.getHashOperation();
        var demande=new OperationBancaireDemande(compteId,null,op.getDateOperation(),null,"Libelle corrige",null,
                op.getDebit(),op.getCredit(),op.getMontant(),op.getSens(),null,null,null,null,null,null,null,null,null);
        tresorerie.modifierOperation(contexte,op.getId(),demande);
        tresorerie.modifierOperation(contexte,op.getId(),demande);
        var conservee=operations.findById(op.getId()).orElseThrow();
        assertThat(conservee.getHashOperation()).isEqualTo(hash);
        assertThat(conservee.getImportReleveBancaire()).isNotNull();
        var autreCompte=comptes.saveAndFlush(nouveauCompte());
        var transfert=new OperationBancaireDemande(autreCompte.getId(),null,op.getDateOperation(),null,"Transfert",null,
                op.getDebit(),op.getCredit(),op.getMontant(),op.getSens(),null,null,null,null,null,null,null,null,null);
        comptesCrees.add(autreCompte.getId());
        assertThatThrownBy(()->tresorerie.modifierOperation(contexte,op.getId(),transfert))
                .hasMessageContaining("compte");
        assertThat(service.importer(contexte,compteId,csv("test.csv",entetes+linkedin+linkedin)).nombreLignesImportees()).isZero();
    }
    @Test void valideLeFichierReelSansLeStockerDansLeDepot() throws Exception {
        String chemin=System.getProperty("wavy.import.test.xlsx");Assumptions.assumeTrue(chemin!=null);
        byte[] contenu=Files.readAllBytes(Path.of(chemin));
        var fichier=new MockMultipartFile("fichier","CA.xlsx","application/octet-stream",contenu);
        var parsing=new ReleveBancaireExcelParser().parser(new java.io.ByteArrayInputStream(contenu));
        assertThat(parsing.lignes()).hasSize(89);assertThat(parsing.erreurs()).isEmpty();
        assertThat(service.importer(contexte,compteId,fichier).nombreLignesImportees()).isEqualTo(89);
        assertThat(service.importer(contexte,compteId,fichier).nombreLignesImportees()).isZero();
        assertThat(service.importer(contexte,compteId,new MockMultipartFile("fichier","renomme.xlsx","application/octet-stream",contenu)).nombreLignesImportees()).isZero();
        var stockees=lignes();assertThat(stockees).hasSize(89).allSatisfy(o->assertThat(o.getDateOperation()).isNotNull());
        assertThat(stockees.stream().filter(o->o.getLibelle().contains("LinkedIn")).count()).isEqualTo(2);
        assertThat(stockees.stream().map(OperationBancaire::getDebit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("68002.40");
        assertThat(stockees.stream().map(OperationBancaire::getCredit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("108473.98");
        for(var ligne:parsing.lignes()) {
            var stockee=stockees.stream().filter(o->Objects.equals(o.getNumeroLigneImport(),ligne.numeroLigne())).findFirst().orElseThrow();
            assertThat(stockee.getDateOperation()).isEqualTo(ligne.dateOperation());
            assertThat(stockee.getLibelle()).isEqualTo(ligne.libelle());
            assertThat(stockee.getDebit()).isEqualByComparingTo(ligne.debit());assertThat(stockee.getCredit()).isEqualByComparingTo(ligne.credit());
        }
        var inversees=new ArrayList<>(parsing.lignes());Collections.reverse(inversees);
        StringBuilder texte=new StringBuilder(entetes);
        for(var l:inversees)texte.append(l.dateOperation().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))).append(';')
            .append('"').append(l.libelle().replace("\"","\"\"")).append('"').append(';').append(l.debit()).append(';').append(l.credit()).append('\n');
        assertThat(service.importer(contexte,compteId,csv("conversion.csv",texte.toString())).nombreLignesImportees()).isZero();
        assertThat(lignes()).hasSize(89);
    }
}
