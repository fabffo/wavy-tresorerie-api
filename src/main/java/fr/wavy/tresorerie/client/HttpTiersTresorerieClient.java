package fr.wavy.tresorerie.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;

@Component
public class HttpTiersTresorerieClient implements TiersTresorerieClient {

    private final RestClient restClient;

    public HttpTiersTresorerieClient(@Value("${wavy.tiers.api-url}") String tiersApiUrl) {
        this.restClient = RestClient.builder().baseUrl(tiersApiUrl).build();
    }

    @Override
    public List<TiersTresorerieDto> listerTiersActifs(ContexteRequete contexte) {
        try {
            TiersTresorerieDto[] reponses = restClient.get()
                    .uri("/api/tiers?actif=true")
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(TiersTresorerieDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    @Override
    public List<TiersTresorerieDto> listerSalaries(ContexteRequete contexte) {
        try {
            TiersTresorerieDto[] reponses = restClient.get()
                    .uri("/api/tiers?role=SALARIE&actif=true")
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(TiersTresorerieDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private void appliquerContexte(HttpHeaders headers, ContexteRequete contexte) {
        headers.set("X-Tenant-Id", String.valueOf(contexte.tenantId()));
        headers.set("X-Societe-Courante-Id", String.valueOf(contexte.societeCouranteId()));
        headers.set("X-Utilisateur-Id", String.valueOf(contexte.utilisateurId()));
        if (contexte.roles() != null && !contexte.roles().isEmpty()) {
            headers.set("X-Wavy-Roles", String.join(",", contexte.roles()));
        }
        if (contexte.email() != null) {
            headers.set("X-Wavy-Email", contexte.email());
        }
    }
}
