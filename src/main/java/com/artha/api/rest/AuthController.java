package com.artha.api.rest;

import com.artha.api.dto.Dtos.*;
import com.artha.infrastructure.security.JwtIssuer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demo auth endpoint. In production this is replaced by OAuth2/OIDC.
 * The point is: the rest of the system is indifferent to how the JWT got minted.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtIssuer issuer;

    public AuthController(JwtIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // Placeholder credential check. Replace with an actual IDP integration.
        if (!"secret".equals(req.password())) {
            return ResponseEntity.status(401).build();
        }
        String token = issuer.issue(req.userId(), List.of("USER"));
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
