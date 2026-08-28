package fr.wavy.tresorerie.configuration;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import fr.wavy.tresorerie.configuration.ProfilSocleClient.ProfilAuthentifie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile({"secure", "production"})
public class ContexteAuthentificationFilter extends OncePerRequestFilter {
    private static final List<String> CHEMINS_PUBLICS = List.of("/actuator/health", "/actuator/info");

    private final ProfilSocleClient profilSocleClient;

    public ContexteAuthentificationFilter(ProfilSocleClient profilSocleClient) {
        this.profilSocleClient = profilSocleClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || CHEMINS_PUBLICS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cookie = request.getHeader(HttpHeaders.COOKIE);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if ((cookie == null || cookie.isBlank()) && (authorization == null || authorization.isBlank())) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        try {
            ProfilAuthentifie profil = profilSocleClient.charger(cookie, authorization);
            if (profil == null || !contexteCorrespond(request, profil)) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
            }
            List<SimpleGrantedAuthority> autorites = profil.roles() == null ? List.of()
                    : profil.roles().stream()
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                            .map(SimpleGrantedAuthority::new)
                            .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(profil.email(), null, autorites));
            filterChain.doFilter(request, response);
        } catch (HttpClientErrorException exception) {
            response.sendError(exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                    ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value());
        } catch (RestClientException exception) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private boolean contexteCorrespond(HttpServletRequest request, ProfilAuthentifie profil) {
        return correspond(request, "X-Tenant-Id", profil.tenantId())
                && correspond(request, "X-Utilisateur-Id", profil.utilisateurId())
                && correspond(request, "X-Societe-Courante-Id", profil.societeCouranteId());
    }

    private boolean correspond(HttpServletRequest request, String header, Long valeurAttendue) {
        return valeurAttendue != null && valeurAttendue.toString().equals(request.getHeader(header));
    }
}
