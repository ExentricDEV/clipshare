package com.exentricdev.clipshare.config;

import com.exentricdev.clipshare.filter.UploadRateLimitFilter;
import com.exentricdev.clipshare.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Role-based access control.
 *
 * <pre>
 *   POST   /api/videos                       ROLE_USER   upload (rate limited, 3/min)
 *   GET    /api/videos/my-videos, /events    ROLE_USER
 *   PATCH  /api/videos/{id}                  ROLE_USER   owner, or ROLE_ADMIN
 *   DELETE /api/videos/{id}                  ROLE_USER   owner, or ROLE_ADMIN
 *   GET    /api/videos/all                   ROLE_ADMIN  moderation view of every video
 *   GET    /api/videos/public                anyone      PUBLIC + READY videos only
 *   GET    /api/videos/{id}, /{id}/playback  anyone      but PRIVATE videos only for the owner/admins
 *   GET    /api/videos/{id}/hls/**, /poster.jpg, /file
 *                                            anyone with a valid signed URL (see PlaybackSigner)
 * </pre>
 *
 * Tokens are validated for signature (Keycloak's public keys), expiry, and issuer.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain security(HttpSecurity http) throws Exception {
        String user = Roles.USER, admin = Roles.ADMIN;
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // signed media URLs: the signature is the access check (enforced in MediaController)
                        .requestMatchers(HttpMethod.GET, "/api/videos/*/hls/**", "/api/videos/*/poster.jpg", "/api/videos/*/file").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/videos/all").hasRole(admin)
                        .requestMatchers(HttpMethod.GET, "/api/videos/my-videos", "/api/videos/events").hasAnyRole(user, admin)
                        .requestMatchers(HttpMethod.GET, "/api/videos/public", "/api/videos/*", "/api/videos/*/playback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/videos").hasAnyRole(user, admin)
                        .requestMatchers(HttpMethod.PATCH, "/api/videos/*").hasAnyRole(user, admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/videos/*").hasAnyRole(user, admin)
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRealmRoleConverter())))
                // after the bearer token is parsed, so the limiter knows who is uploading
                .addFilterAfter(new UploadRateLimitFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Validates signature (via Keycloak's JWKS), expiry, and issuer. The JWKS is fetched over the internal
     * Docker network, while tokens are issued for the browser-facing hostname, so the expected issuer is
     * configured separately.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                                 @Value("${app.security.issuer:}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = issuer.isBlank()
                ? JwtValidators.createDefault()
                : new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Keycloak puts realm roles in {@code realm_access.roles}; Spring expects authorities like ROLE_USER. */
    static class KeycloakRealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
        }

        static Collection<GrantedAuthority> authorities(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) return List.of();
            return roles.stream()
                    .map(String::valueOf)
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                    .toList();
        }
    }
}
