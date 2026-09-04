package fr.wavy.tresorerie.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    private final List<String> allowedOriginPatterns;

    public SecurityConfig(@Value("${wavy.cors.allowed-origin-patterns:}") String allowedOriginPatterns) {
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toList();
        if (this.allowedOriginPatterns.contains("*")) {
            throw new IllegalArgumentException("wavy.cors.allowed-origin-patterns ne doit pas contenir l'origine globale '*'.");
        }
    }

    @Bean
    @Profile("!secure & !production")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll()
                )
                .build();
    }

    @Bean
    @Profile({"secure", "production"})
    public SecurityFilterChain secureSecurityFilterChain(HttpSecurity http,
            ContexteAuthentificationFilter contexteAuthentificationFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(contexteAuthentificationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Profile({"secure", "production"})
    public FilterRegistrationBean<ContexteAuthentificationFilter> disableServletRegistration(
            ContexteAuthentificationFilter filter) {
        FilterRegistrationBean<ContexteAuthentificationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4206",
                "http://localhost:14206",
                "http://localhost:24206"
        ));
        if (!allowedOriginPatterns.isEmpty()) {
            configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Tenant-Id",
                "X-Utilisateur-Id",
                "X-Societe-Courante-Id",
                "X-Roles",
                "X-Wavy-Roles",
                "X-Wavy-Email"
        ));
        configuration.setExposedHeaders(List.of(HttpHeaders.SET_COOKIE));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        LOGGER.info("CORS Tresorerie autorise origins={}, originPatterns={}, methods={}, headers={}",
                configuration.getAllowedOrigins(),
                configuration.getAllowedOriginPatterns(),
                configuration.getAllowedMethods(),
                configuration.getAllowedHeaders());
        return source;
    }
}
