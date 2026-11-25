package com.example.bulksmsmockgateway.security;

import com.example.bulksmsmockgateway.config.SandboxSettings;
import com.example.bulksmsmockgateway.dtos.response.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration
public class TokenSecurity {
    @Bean
    AuthenticationManager tokenAuthentication(SandboxSettings settings) {
        return authentication -> {
            boolean idMatches = matches(settings.tokenId(), authentication.getName());
            boolean secretMatches = matches(settings.tokenSecret(), String.valueOf(authentication.getCredentials()));
            if (!idMatches || !secretMatches) {
                throw new BadCredentialsException("Invalid API token");
            }
            return UsernamePasswordAuthenticationToken.authenticated(authentication.getName(), null, List.of());
        };
    }

    @Bean
    SecurityFilterChain tokenFilter(HttpSecurity http, AuthenticationManager tokenAuthentication, ObjectMapper json) {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"BulkSMS\", charset=\"UTF-8\"");
            response.setContentType("application/json");
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            boolean supplied = header != null && header.regionMatches(true, 0, "Basic", 0, 5);
            String code = supplied ? "authentication-failed" : "authentication-required";
            json.writeValue(response.getOutputStream(), ApiError.of(401, code, "Supply a valid API token ID and secret"));
        };
        return http
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(Customizer.withDefaults())
                .authenticationManager(tokenAuthentication)
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/healthz", "/swagger-ui.html", "/swagger-ui/index.html",
                                "/swagger-ui/sandbox.js", "/v3/api-docs", "/webjars/swagger-ui/swagger-ui.css",
                                "/webjars/swagger-ui/swagger-ui-bundle.js").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/sandbox/attachments/*/upload").permitAll()
                        .requestMatchers(HttpMethod.GET, "/sandbox/attachments/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    private static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
