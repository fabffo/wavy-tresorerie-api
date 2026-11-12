package fr.wavy.tresorerie.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import fr.wavy.tresorerie.client.TiersTresorerieClient;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.exception.RegleMetierException;
import fr.wavy.tresorerie.modele.CompteBancaire;
import fr.wavy.tresorerie.repository.*;
import fr.wavy.tresorerie.service.importreleve.*;

class ImportReleveBancaireServiceTest {
    private final CompteBancaireRepository comptes = mock(CompteBancaireRepository.class);
    private final ImportReleveBancaireRepository imports = mock(ImportReleveBancaireRepository.class);
    private final OperationBancaireRepository operations = mock(OperationBancaireRepository.class);
    private final TiersTresorerieClient tiers = mock(TiersTresorerieClient.class);
    private final ContexteRequete contexte = new ContexteRequete(1L, 2L, 3L, List.of(), null);
    private final ImportReleveBancaireService service = new ImportReleveBancaireService(
            comptes, imports, operations, new ReleveBancaireCsvParser(), new ReleveBancaireExcelParser(),
            mock(ClassificationOperationBancaireService.class), tiers);

    private void preparerCompte() {
        CompteBancaire compte = mock(CompteBancaire.class);
        when(compte.getId()).thenReturn(4L);
        when(comptes.verrouillerPourImport(4L, 1L, 3L))
                .thenReturn(Optional.of(compte));
    }

    private MockMultipartFile fichier(String contenu) {
        return new MockMultipartFile("fichier", "releve.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void normaliseLesMontantsEtDistingueLesOccurrences() {
        var date = java.time.LocalDate.of(2026, 1, 12);
        var premiere = new LigneReleveBancaire(20, date, null, "LinkedIn", null, null, null,
                new java.math.BigDecimal("-168.59"));
        var equivalente = new LigneReleveBancaire(99, date, null, "LinkedIn", null, null, null,
                new java.math.BigDecimal("-168.5900"));
        assertThat(service.hashOccurrence(contexte, 4L, premiere, 1))
                .isEqualTo(service.hashOccurrence(contexte, 4L, equivalente, 1))
                .isNotEqualTo(service.hashOccurrence(contexte, 4L, premiere, 2))
                .isNotEqualTo(service.hashOccurrence(contexte, 5L, premiere, 1));
    }

    @Test
    void refuseLesEntetesInvalidesAvantTouteEcriture() {
        preparerCompte();
        assertThatThrownBy(() -> service.importer(contexte, 4L,
                fichier("Jour;Libellé;Débit euros;Crédit euros\n02/01/2026;Cotisation;109,00;")))
                .isInstanceOf(RegleMetierException.class).hasMessageContaining("colonne de date");
        verifyNoInteractions(imports, operations, tiers);
    }

    @Test
    void nePersisteAucuneOperationSansDateEtIgnoreLesDoublons() {
        preparerCompte();
        when(imports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tiers.listerSalaries(contexte)).thenReturn(List.of());
        var existante = new fr.wavy.tresorerie.modele.OperationBancaire();
        existante.setDateOperation(java.time.LocalDate.of(2026, 1, 2));
        existante.setLibelle("Cotisation");
        existante.setMontant(new java.math.BigDecimal("-109.00"));
        existante.setHashOperation("ancien-hash");
        when(operations.findByTenantIdAndSocieteInterneIdAndCompteBancaireId(1L, 3L, 4L))
                .thenReturn(List.of(existante));
        var resultat = service.importer(contexte, 4L, fichier(
                "Date d'opération;Libellé;Débit euros;Crédit euros\n;Sans date;109,00;\n02/01/2026;Cotisation;109,00;"));
        assertThat(resultat.nombreLignesImportees()).isZero();
        assertThat(resultat.nombreLignesRejetees()).isEqualTo(2);
        assertThat(resultat.erreurs()).anySatisfy(erreur -> assertThat(erreur).contains("date d’operation absente"))
                .anySatisfy(erreur -> assertThat(erreur).contains("doublon"));
        verify(operations, never()).save(any());

    }
}
