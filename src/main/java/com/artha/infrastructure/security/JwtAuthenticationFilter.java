package com.artha.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Validates Bearer JWTs on every request. Tokens must be signed with our secret
 * and contain a subject + roles claim. Invalid or missing tokens don't throw —
 * they just skip authentication, so downstream Spring Security decides whether
 * that endpoint requires auth.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${artha.security.jwt-secret:change-me-in-production-this-must-be-at-least-256-bits}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
                String subject = claims.getSubject();
                Object rolesClaim = claims.get("roles");
                List<SimpleGrantedAuthority> authorities = List.of();
                if (rolesClaim instanceof String rolesStr) {
                    authorities = Arrays.stream(rolesStr.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .toList();
                }
                var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ignored) {
                // malformed / expired / tampered — leave unauthenticated
            }
        }
        chain.doFilter(req, res);
    }
}
