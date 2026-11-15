package fr.wavy.tresorerie.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import fr.wavy.tresorerie.configuration.ProfilSocleClient.ProfilAuthentifie;
import fr.wavy.tresorerie.controleur.TresorerieControleur;
import fr.wavy.tresorerie.service.ImportReleveBancaireService;
import fr.wavy.tresorerie.service.TresorerieService;

@WebMvcTest(controllers = TresorerieControleur.class)
@Import({SecurityConfig.class, ContexteAuthentificationFilter.class})
@ActiveProfiles("recette")
@TestPropertySource(properties =
        "wavy.cors.allowed-origin-patterns=http://localhost:24200,http://localhost:24201,http://localhost:24202,http://localhost:24203,http://localhost:24204,http://localhost:24206,http://localhost:24207")
class SecuriteRecetteTest {

    @Test
    void origineGlobaleAvecCredentialsEstRefusee() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SecurityConfig("*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Autowired MockMvc mockMvc;
    @Autowired Environment environment;
    @Autowired ContexteAuthentificationFilter contexteAuthentificationFilter;

    @MockitoBean ProfilSocleClient profilSocleClient;
    @MockitoBean TresorerieService tresorerieService;
    @MockitoBean ImportReleveBancaireService importReleveBancaireService;
    @MockitoBean ContexteRequeteResolver contexteRequeteResolver;

    @BeforeEach
    void preparer() {
        when(profilSocleClient.charger(any(), any()))
                .thenReturn(new ProfilAuthentifie(7L, "utilisateur@example.invalid", 3L, 11L, List.of("USER")));
        when(contexteRequeteResolver.resoudre(any()))
                .thenReturn(new ContexteRequete(3L, 7L, 11L, List.of("USER"), null));
        when(tresorerieService.listerComptes(any())).thenReturn(List.of());
    }

    @Test
    void recetteActiveSecureEtHealthRestePublic() throws Exception {
        org.assertj.core.api.Assertions.assertThat(environment.getActiveProfiles()).contains("recette", "secure");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        contexteAuthentificationFilter.doFilter(request, response, new MockFilterChain());
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void endpointMetierSansAuthentificationEstRefuse() throws Exception {
        mockMvc.perform(get("/api/tresorerie/comptes-bancaires")).andExpect(status().isUnauthorized());
    }

    @Test
    void parcoursCarteExigeAuthentificationEtConfirmationExplicite() throws Exception {
        mockMvc.perform(get("/api/tresorerie/operations-bancaires/rapprochement-cartes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/tresorerie/operations-bancaires/1/rapprochement-carte")
                .header("Authorization", "Bearer jeton-test").header("X-Tenant-Id",3)
                .header("X-Utilisateur-Id",7).header("X-Societe-Courante-Id",11)
                .contentType("application/json").content("{\"factureId\":10,\"tiersConfirme\":7}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(tresorerieService, org.mockito.Mockito.never()).confirmerCarte(any(), any(), any());
    }

    @Test
    void headersForgesSeulsSontRefuses() throws Exception {
        mockMvc.perform(requeteMetier(3, 7, 11)).andExpect(status().isUnauthorized());
    }

    @Test
    void identiteEtContexteCoherentsSontAcceptes() throws Exception {
        mockMvc.perform(requeteMetier(3, 7, 11).header("Authorization", "Bearer jeton-test"))
                .andExpect(status().isOk());
    }

    @Test
    void preflightPutDepuisErpShellRecetteEstAccepte() throws Exception {
        mockMvc.perform(options("/api/tresorerie/comptes-bancaires/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:24207")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization,Content-Type,X-Tenant-Id,X-Utilisateur-Id,X-Societe-Courante-Id,X-Wavy-Email,X-Wavy-Roles,X-Roles"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:24207"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Set-Cookie"));
    }

    @Test
    void preflightDepuisUneOrigineNonAutoriseeEstRefuse() throws Exception {
        mockMvc.perform(options("/api/tresorerie/comptes-bancaires/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:29999")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void autreTenantEstRefuse() throws Exception {
        mockMvc.perform(requeteMetier(4, 7, 11).header("Authorization", "Bearer jeton-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void autreUtilisateurEstRefuse() throws Exception {
        mockMvc.perform(requeteMetier(3, 8, 11).header("Authorization", "Bearer jeton-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void societeNonAutoriseeEstRefusee() throws Exception {
        mockMvc.perform(requeteMetier(3, 7, 12).header("Authorization", "Bearer jeton-test"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requeteMetier(
            long tenantId, long utilisateurId, long societeId) {
        return get("/api/tresorerie/comptes-bancaires")
                .header("X-Tenant-Id", tenantId)
                .header("X-Utilisateur-Id", utilisateurId)
                .header("X-Societe-Courante-Id", societeId);
    }
}
