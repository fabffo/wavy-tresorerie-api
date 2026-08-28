package fr.wavy.tresorerie.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.configuration.AuthentificationSortante;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;
import fr.wavy.tresorerie.exception.ErreurInterserviceException;

@Component
public class HttpTiersTresorerieClient implements TiersTresorerieClient {

    private final RestClient restClient;
    private final AuthentificationSortante authentificationSortante;

    public HttpTiersTresorerieClient(@Value("${wavy.tiers.api-url}") String tiersApiUrl,
            AuthentificationSortante authentificationSortante) {
        this.restClient = RestClient.builder().baseUrl(tiersApiUrl).build();
        this.authentificationSortante = authentificationSortante;
    }

    @Override
    public List<TiersTresorerieDto> listerTiersActifs(ContexteRequete contexte) {
        return executer(() -> {
            TiersTresorerieDto[] reponses = restClient.get()
                    .uri("/api/tiers?actif=true")
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(TiersTresorerieDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    @Override
    public List<TiersTresorerieDto> listerSalaries(ContexteRequete contexte) {
        return executer(() -> {
            TiersTresorerieDto[] reponses = restClient.get()
                    .uri("/api/tiers?role=SALARIE&actif=true")
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(TiersTresorerieDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    private void appliquerContexte(HttpHeaders headers, ContexteRequete contexte) {
        authentificationSortante.propager(headers);
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

    private <T> T executer(java.util.function.Supplier<T> appel) {
        try {
            return appel.get();
        } catch (RestClientResponseException exception) {
            throw new ErreurInterserviceException(exception.getStatusCode(),
                    "Le service Tiers a refuse la requete.", exception);
        } catch (RestClientException exception) {
            throw new ErreurInterserviceException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service Tiers est indisponible.", exception);
        }
    }
}
