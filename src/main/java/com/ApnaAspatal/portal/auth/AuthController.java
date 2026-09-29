package com.ApnaAspatal.portal.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.auth.dto.RegisterRequest;
import com.ApnaAspatal.portal.auth.dto.TokenRequest;
import com.ApnaAspatal.portal.auth.dto.TokenResponse;
import com.ApnaAspatal.portal.auth.dto.UserResponse;

/**
 * Public endpoints for creating an account and signing in. Every other endpoint
 * requires the token issued here, sent as {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = authService.register(request.username(), request.password());
        return new UserResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        return authService.issueToken(request.username(), request.password());
    }
}
