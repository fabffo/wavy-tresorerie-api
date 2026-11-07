package fr.wavy.tresorerie.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fr.wavy.tresorerie.configuration.AuthentificationSortante;
import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.exception.ErreurInterserviceException;

class PropagationAuthentificationClientsTest {
    private HttpServer serveur;

    @AfterEach
    void nettoyer() {
        RequestContextHolder.resetRequestAttributes();
        if (serveur != null) serveur.stop(0);
    }

    @Test
    void tiersPropageCookieAuthorizationEtContexte() throws Exception {
        AtomicReference<com.sun.net.httpserver.Headers> entetes = new AtomicReference<>();
        String baseUrl = demarrer(exchange -> {
            entetes.set(exchange.getRequestHeaders());
            repondre(exchange, 200, "[]");
        });
        HttpTiersTresorerieClient client = new HttpTiersTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();

        assertThat(client.listerTiersActifs(contexte())).isEmpty();
        verifierEntetes(entetes.get());
    }

    @Test
    void facturesPropageCookieAuthorizationEtContexte() throws Exception {
        AtomicReference<com.sun.net.httpserver.Headers> entetes = new AtomicReference<>();
        String baseUrl = demarrer(exchange -> {
            entetes.set(exchange.getRequestHeaders());
            repondre(exchange, 200, "[]");
        });
        HttpFacturesTresorerieClient client = new HttpFacturesTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();

        assertThat(client.listerReglesRapprochementFournisseur(contexte())).isEmpty();
        verifierEntetes(entetes.get());
    }

    @Test
    void refusTiersNestPasTransformeEnListeVide() throws Exception {
        String baseUrl = demarrer(exchange -> repondre(exchange, 403, ""));
        HttpTiersTresorerieClient client = new HttpTiersTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();

        assertThatThrownBy(() -> client.listerTiersActifs(contexte()))
                .isInstanceOf(ErreurInterserviceException.class)
                .extracting("statut.value").isEqualTo(403);
    }

    @Test
    void refusFacturesNestPasTransformeEnListeVide() throws Exception {
        String baseUrl = demarrer(exchange -> repondre(exchange, 401, ""));
        HttpFacturesTresorerieClient client = new HttpFacturesTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();

        assertThatThrownBy(() -> client.listerReglesRapprochementFournisseur(contexte()))
                .isInstanceOf(ErreurInterserviceException.class)
                .extracting("statut.value").isEqualTo(401);
    }

    @Test
    void remboursementTransmetSonTypeSansMontantVide() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        String baseUrl = demarrer(exchange -> { query.set(exchange.getRequestURI().getRawQuery()); repondre(exchange, 200, "[]"); });
        HttpFacturesTresorerieClient client = new HttpFacturesTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();
        client.listerFacturesAchatNotesFraisOuvertes(contexte(), 7L, null);
        assertThat(query.get()).contains("typeFacture=ACHAT", "tiersId=7", "remboursementBeneficiaire=true").doesNotContain("montant");
    }

    @Test
    void paiementTransmetLesReferencesDurables() throws Exception {
        AtomicReference<String> corps = new AtomicReference<>();
        String baseUrl = demarrer(exchange -> { corps.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); repondre(exchange, 200, "{}"); });
        HttpFacturesTresorerieClient client = new HttpFacturesTresorerieClient(baseUrl, new AuthentificationSortante());
        preparerRequeteEntrante();
        client.enregistrerPaiement(contexte(), fr.wavy.tresorerie.modele.TypeFactureRapprochement.ACHAT, 9L,
                new fr.wavy.tresorerie.dto.EnregistrementPaiementFactureDto(new java.math.BigDecimal("12.00"), java.time.LocalDate.of(2026,2,1), "RAP-42", 11L, 42L, 7L, true));
        assertThat(corps.get()).contains("\"operationBancaireId\":11", "\"rapprochementId\":42", "\"tiersId\":7", "\"remboursementBeneficiaire\":true");
    }

    private String demarrer(Gestionnaire gestionnaire) throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", exchange -> gestionnaire.gerer(exchange));
        serveur.start();
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    private void preparerRequeteEntrante() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "SESSION=test-session");
        request.addHeader("Authorization", "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ContexteRequete contexte() {
        return new ContexteRequete(3L, 7L, 11L, List.of("USER"), "utilisateur@example.invalid");
    }

    private void verifierEntetes(com.sun.net.httpserver.Headers entetes) {
        assertThat(entetes.getFirst("Cookie")).isEqualTo("SESSION=test-session");
        assertThat(entetes.getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(entetes.getFirst("X-Tenant-Id")).isEqualTo("3");
        assertThat(entetes.getFirst("X-Utilisateur-Id")).isEqualTo("7");
        assertThat(entetes.getFirst("X-Societe-Courante-Id")).isEqualTo("11");
    }

    private void repondre(HttpExchange exchange, int statut, String corps) throws IOException {
        byte[] contenu = corps.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statut, contenu.length);
        exchange.getResponseBody().write(contenu);
        exchange.close();
    }

    @FunctionalInterface
    private interface Gestionnaire {
        void gerer(HttpExchange exchange) throws IOException;
    }
}
