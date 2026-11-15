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
        return new PieceCarteRapprochementDto(4L,tenant,societe,7L,"FA-4",date,LocalDate.of(2020,1,1),new BigDecimal(montant),nom,nom,"Pièce");
    }
    PieceCarteRapprochementDto piece() { return piece(2L,3L,"40.00",LocalDate.of(2026,1,23),"Parking Madeleine"); }
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
