package fr.wavy.tresorerie.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ProfilSocleClient {
    private final RestClient restClient;

    public ProfilSocleClient(@Value("${wavy.socle.api-url}") String socleApiUrl) {
        this.restClient = RestClient.builder().baseUrl(socleApiUrl).build();
    }

    public ProfilAuthentifie charger(String cookie, String authorization) {
        return restClient.get()
                .uri("/api/profil")
                .headers(headers -> {
                    if (cookie != null && !cookie.isBlank()) {
                        headers.set(HttpHeaders.COOKIE, cookie);
                    }
                    if (authorization != null && !authorization.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new HttpClientErrorException(response.getStatusCode());
                })
                .body(ProfilAuthentifie.class);
    }

    public record ProfilAuthentifie(Long utilisateurId, String email, Long tenantId,
                                     Long societeCouranteId, List<String> roles) {
    }
}
