package fr.wavy.tresorerie.service;

import fr.wavy.tresorerie.dto.*;
import fr.wavy.tresorerie.modele.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RapprochementCarteMatcherTest {
    OperationBancaire op(String libelle) {
        var o = new OperationBancaire(); org.springframework.test.util.ReflectionTestUtils.setField(o,"id",1L); o.setTenantId(2L); o.setSocieteInterneId(3L);
        o.setSens(SensOperation.DEBIT); o.setDebit(new BigDecimal("40.00")); o.setDateOperation(LocalDate.of(2026,1,26));
        o.setLibelle(libelle); o.setNatureOperation(NatureOperationBancaire.SALAIRE); return o;
    }
    PieceCarteRapprochementDto piece(Long tenant, Long societe, String montant, LocalDate date, String nom) {
        return new PieceCarteRapprochementDto(4L,tenant,societe,7L,"FA-4",date,LocalDate.of(2020,1,1),new BigDecimal(montant),nom,nom,"Pièce",date,new BigDecimal(montant));
    }
    PieceCarteRapprochementDto piece() { return piece(2L,3L,"40.00",LocalDate.of(2026,1,23),"Parking Madeleine"); }
    @Test void fournisseursRetientJusquaTroisJoursSansComparerLeLibelle() {
        var operation = op("Alias sans concordance");
        operation.setTiersId(7L);
        operation.setTypeOperation(TypeOperationBancaire.PAIEMENT_CB);
        var proposition = RapprochementCarteMatcher.comparerFournisseurs(operation, piece(), null).orElseThrow();
        assertThat(proposition.dateDepense()).isEqualTo(LocalDate.of(2026, 1, 23));
        assertThat(proposition.montantRapproche()).isEqualByComparingTo("40");
        operation.setDateOperation(LocalDate.of(2026, 1, 27));
        assertThat(RapprochementCarteMatcher.comparerFournisseurs(operation, piece(), null)).isEmpty();
        operation.setDateOperation(LocalDate.of(2026, 1, 22));
        assertThat(RapprochementCarteMatcher.comparerFournisseurs(operation, piece(), null)).isEmpty();
    }

    @Test void fournisseursUtiliseLaDateFactureEtLeTtcAuChangementDAnnee() {
        var operation = op("CARTE 20/12");
        operation.setTiersId(7L);
        operation.setTypeOperation(TypeOperationBancaire.PAIEMENT_CB);
        operation.setDateOperation(LocalDate.of(2026, 1, 3));
        var dateFacture = LocalDate.of(2025, 12, 31);
        var piece = new PieceCarteRapprochementDto(4L, 2L, 3L, 7L, "FA-4", LocalDate.of(2025, 12, 20),
                dateFacture, new BigDecimal("40"), "Tiers", "Nom", "Pièce", null, new BigDecimal("40"));
        assertThat(RapprochementCarteMatcher.comparerFournisseurs(operation, piece, null)).isPresent();
        operation.setDebit(new BigDecimal("39.99"));
        assertThat(RapprochementCarteMatcher.comparerFournisseurs(operation, piece, null)).isEmpty();
        operation.setTiersId(99L);
        operation.setDebit(new BigDecimal("40"));
        assertThat(RapprochementCarteMatcher.comparerFournisseurs(operation, piece, null)).isEmpty();
    }

    @Test void fournisseursBloqueDeuxOperationsOuDeuxFacturesCompatibles() {
        var operation = op("CARTE");
        operation.setTiersId(7L);
        operation.setTypeOperation(TypeOperationBancaire.PAIEMENT_CB);
        var autre = op("AUTRE CARTE");
        org.springframework.test.util.ReflectionTestUtils.setField(autre, "id", 2L);
        autre.setTiersId(7L);
        autre.setTypeOperation(TypeOperationBancaire.PAIEMENT_CB);
        var candidats = RapprochementCarteMatcher.propositionsFournisseurs(operation,
                java.util.List.of(piece()), java.util.List.of(autre), java.util.List.of(), null);
        assertThat(candidats).singleElement().satisfies(candidat -> assertThat(candidat.ambiguite()).isTrue());
        var date = LocalDate.of(2026, 1, 23);
        var seconde = new PieceCarteRapprochementDto(5L, 2L, 3L, 7L, "FA-5", date, date, new BigDecimal("40"),
                "Tiers", "Nom", "Pièce", date, new BigDecimal("40"));
        assertThat(RapprochementCarteMatcher.propositionsFournisseurs(operation,
                java.util.List.of(piece(), seconde), java.util.List.of(), java.util.List.of(), null))
                .hasSize(2).allMatch(PropositionCarteDto::conflit);
    }

    @Test void sansTiersEtDateComptabilisationDecalee() {
        var o=op("Paiement par carte PARKING MADELEIN PAR 23/01");
        var p=RapprochementCarteMatcher.comparer(o,piece()).orElseThrow();
        assertThat(p.dateAchat()).isEqualTo(LocalDate.of(2026,1,23));
        assertThat(p.dateAtypique()).isFalse(); assertThat(p.commercantReconnu()).isTrue();
        assertThat(p.affectationRequise()).isTrue(); assertThat(o.getTiersId()).isNull();
    }
    @Test void variantesDeNomsRestentProposablesAvecAvertissement() {
        var p=RapprochementCarteMatcher.comparer(op("PAIEMENT BRIOCHE DOREE 23/01"),piece(2L,3L,"40",LocalDate.of(2026,1,23),"SSP France")).orElseThrow();
        assertThat(p.commercantReconnu()).isFalse(); assertThat(p.criteres()).anyMatch(c->c.contains("alias"));
    }
    @Test void datesAtypiquesOuAbsentesNeSontPasMasquees() {
        assertThat(RapprochementCarteMatcher.comparer(op("PAIEMENT 23/01"),piece(2L,3L,"40",LocalDate.of(2025,1,23),"Q8")).orElseThrow().dateAtypique()).isTrue();
        assertThat(RapprochementCarteMatcher.comparer(op("PAIEMENT"),piece(2L,3L,"40",null,"Q8")).orElseThrow().dateAtypique()).isTrue();
    }
    @Test void toleranceCentimeSansDepassement() {
        var p=RapprochementCarteMatcher.comparer(op("PAIEMENT"),piece(2L,3L,"40.01",LocalDate.of(2026,1,23),"Q8")).orElseThrow();
        assertThat(p.montantRapproche()).isEqualByComparingTo("40"); assertThat(p.ecart()).isEqualByComparingTo("0.01");
        assertThat(RapprochementCarteMatcher.comparer(op("PAIEMENT"),piece(2L,3L,"40.02",LocalDate.of(2026,1,23),"Q8"))).isEmpty();
    }
    @Test void isolationEtCreditOuOperationDejaRapprochee() {
        var o=op("PAIEMENT");
        assertThat(RapprochementCarteMatcher.comparer(o,piece(8L,3L,"40",LocalDate.now(),"Q8"))).isEmpty();
        assertThat(RapprochementCarteMatcher.comparer(o,piece(2L,8L,"40",LocalDate.now(),"Q8"))).isEmpty();
        o.setSens(SensOperation.CREDIT); assertThat(RapprochementCarteMatcher.comparer(o,piece())).isEmpty();
        o.setSens(SensOperation.DEBIT);o.setRapprochee(true);assertThat(RapprochementCarteMatcher.comparer(o,piece())).isEmpty();
    }
    @Test void tiersDifferentSignaleUnConflit() {
        var o=op("PAIEMENT");o.setTiersId(99L);
        assertThat(RapprochementCarteMatcher.comparer(o,piece()).orElseThrow().conflit()).isTrue();
    }
    @Test void changementAnneeEtDateInvalide() {
        assertThat(RapprochementCarteMatcher.dateAchat("CARTE 31/12",LocalDate.of(2026,1,2))).isEqualTo(LocalDate.of(2025,12,31));
        assertThat(RapprochementCarteMatcher.dateAchat("CARTE 31/02",LocalDate.of(2026,3,1))).isNull();
    }
}
