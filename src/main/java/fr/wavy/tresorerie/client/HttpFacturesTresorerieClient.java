package fr.wavy.tresorerie.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.configuration.AuthentificationSortante;
import fr.wavy.tresorerie.dto.BalanceAgeeReponse;
import fr.wavy.tresorerie.dto.CreationFactureAchatDepuisOperationBancaireDemande;
import fr.wavy.tresorerie.dto.EnregistrementPaiementFactureDto;
import fr.wavy.tresorerie.dto.FactureAchatRapprochementReponse;
import fr.wavy.tresorerie.dto.FactureEcheanceReponse;
import fr.wavy.tresorerie.dto.PageEcheancesReponse;
import fr.wavy.tresorerie.dto.KpiEcheancesReponse;
import fr.wavy.tresorerie.dto.FactureOuverteRapprochementDto;
import fr.wavy.tresorerie.dto.MiseAJourLibelleRapprochementDemande;
import fr.wavy.tresorerie.dto.RegleRapprochementFournisseurFacturesDto;
import fr.wavy.tresorerie.modele.TypeFactureRapprochement;
import fr.wavy.tresorerie.exception.ErreurInterserviceException;

@Component
public class HttpFacturesTresorerieClient implements FacturesTresorerieClient {

    private final RestClient restClient;
    private final AuthentificationSortante authentificationSortante;

    public HttpFacturesTresorerieClient(@Value("${wavy.factures.api-url}") String facturesApiUrl,
            AuthentificationSortante authentificationSortante) {
        this.restClient = RestClient.builder().baseUrl(facturesApiUrl).build();
        this.authentificationSortante = authentificationSortante;
    }

    @Override
    public PageEcheancesReponse<FactureEcheanceReponse> listerEcheances(ContexteRequete contexte, String queryString) {
        String uri = "/api/factures/echeances" + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);
        return executer(() -> restClient.get().uri(uri).headers(headers -> appliquerContexte(headers, contexte))
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {}));
    }

    @Override
    public KpiEcheancesReponse kpisEcheances(ContexteRequete contexte) {
        return executer(() -> restClient.get().uri("/api/factures/echeances/kpis")
                .headers(headers -> appliquerContexte(headers, contexte))
                .retrieve().body(KpiEcheancesReponse.class));
    }

    @Override
    public BalanceAgeeReponse balanceAgee(ContexteRequete contexte) {
        return executer(() -> restClient.get().uri("/api/factures/balance-agee")
                .headers(headers -> appliquerContexte(headers, contexte))
                .retrieve().body(BalanceAgeeReponse.class));
    }

    @Override
    public List<RegleRapprochementFournisseurFacturesDto> listerReglesRapprochementFournisseur(ContexteRequete contexte) {
        return executer(() -> {
            RegleRapprochementFournisseurFacturesDto[] reponses = restClient.get()
                    .uri("/api/factures/achats/rapprochement-fournisseurs/regles?typeRegle=OPERATION_BANCAIRE")
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(RegleRapprochementFournisseurFacturesDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    @Override
    public FactureAchatRapprochementReponse creerFactureAchatDepuisOperationBancaire(
            ContexteRequete contexte,
            CreationFactureAchatDepuisOperationBancaireDemande demande
    ) {
        return executer(() -> restClient.post()
                .uri("/api/factures/achats/depuis-operation-bancaire")
                .headers(headers -> appliquerContexte(headers, contexte))
                .body(demande)
                .retrieve()
                .body(FactureAchatRapprochementReponse.class));
    }

    @Override
    public FactureAchatRapprochementReponse mettreAJourLibelleRapprochement(
            ContexteRequete contexte,
            Long factureId,
            MiseAJourLibelleRapprochementDemande demande
    ) {
        return executer(() -> restClient.put()
                .uri("/api/factures/achats/{id}/libelle-rapprochement", factureId)
                .headers(headers -> appliquerContexte(headers, contexte))
                .body(demande)
                .retrieve()
                .body(FactureAchatRapprochementReponse.class));
    }

    @Override
    public List<FactureOuverteRapprochementDto> listerFacturesOuvertes(
            ContexteRequete contexte,
            TypeFactureRapprochement typeFacture,
            Long tiersId
    ) {
        return executer(() -> {
            FactureOuverteRapprochementDto[] reponses = restClient.get()
                    .uri(uri -> uri.path("/api/factures/rapprochement/ouvertes")
                            .queryParam("typeFacture", typeFacture.name())
                            .queryParam("tiersId", tiersId)
                            .build())
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(FactureOuverteRapprochementDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    @Override
    public List<FactureOuverteRapprochementDto> listerFacturesAchatNotesFraisOuvertes(
            ContexteRequete contexte,
            Long tiersId,
            BigDecimal montant
    ) {
        return executer(() -> {
            FactureOuverteRapprochementDto[] reponses = restClient.get()
                    .uri(uri -> uri.path("/api/factures/rapprochement/ouvertes")
                            .queryParam("typeFacture", TypeFactureRapprochement.ACHAT.name())
                            .queryParam("tiersId", tiersId)
                            .queryParam("categorieAchat", "FRAIS_GENERAUX")
                            .queryParam("montant", montant)
                            .build())
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(FactureOuverteRapprochementDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    @Override
    public List<FactureOuverteRapprochementDto> listerSalairesOuverts(
            ContexteRequete contexte, Long tiersId, int annee, int mois, BigDecimal montant) {
        return executer(() -> {
            FactureOuverteRapprochementDto[] reponses = restClient.get()
                    .uri(uri -> uri.path("/api/salaires/ouverts")
                            .queryParam("tiersId", tiersId)
                            .queryParam("annee", annee)
                            .queryParam("mois", mois)
                            .queryParam("montant", montant)
                            .build())
                    .headers(headers -> appliquerContexte(headers, contexte))
                    .retrieve()
                    .body(FactureOuverteRapprochementDto[].class);
            return reponses == null ? List.of() : List.of(reponses);
        });
    }

    @Override
    public FactureOuverteRapprochementDto enregistrerPaiement(
            ContexteRequete contexte,
            TypeFactureRapprochement typeFacture,
            Long factureId,
            EnregistrementPaiementFactureDto demande
    ) {
        return executer(() -> restClient.post()
                .uri("/api/factures/rapprochement/{typeFacture}/{id}/paiement", typeFacture.name(), factureId)
                .headers(headers -> appliquerContexte(headers, contexte))
                .body(demande)
                .retrieve()
                .body(FactureOuverteRapprochementDto.class));
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
                    "Le service Factures a refuse la requete.", exception);
        } catch (RestClientException exception) {
            throw new ErreurInterserviceException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service Factures est indisponible.", exception);
        }
    }
}
