package org.evchargingplatform.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.net.URI;

/**
 * HTTP security for the whole API: a stateless OAuth2 resource server validating Keycloak JWTs.
 * <p>
 * Coarse rules live here (public, authenticated, operator-only). "Only your own vehicle, reservation
 * or session" depends on the resource, so web adapters check it with
 * {@link AuthenticatedUser#requireOwnerOrOperator}. 401 and 403 are written as problem details,
 * matching {@code ApiExceptionHandler}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                // Bearer tokens only, no cookies or sessions, so CSRF protection does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/stations", "/stations/*", "/stations/*/connectors").permitAll()
                        .requestMatchers(HttpMethod.POST, "/stations", "/stations/*/connectors").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("OPERATOR")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRealmRoleConverter()))
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(objectMapper, request,
                                response, HttpStatus.UNAUTHORIZED, "Authentication required",
                                "Send a valid bearer token in the Authorization header"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(objectMapper, request,
                                response, HttpStatus.FORBIDDEN, "Forbidden",
                                "Your account does not have the role this endpoint requires")));
        return http.build();
    }

    private static void writeProblem(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
                                     HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.evchargingplatform.org/errors/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
