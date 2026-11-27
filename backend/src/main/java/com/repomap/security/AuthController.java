package com.repomap.security;

import com.repomap.security.AuthDtos.LoginRequest;
import com.repomap.security.AuthDtos.RegisterRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserService.TokenView register(@Valid @RequestBody RegisterRequest request) {
        return CurrentUserService.TokenView.from(
                authService.register(request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    public CurrentUserService.TokenView login(@Valid @RequestBody LoginRequest request) {
        return CurrentUserService.TokenView.from(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            authService.logout(authorization.substring(7).trim());
        }
    }

    @GetMapping("/me")
    public CurrentUserService.UserView me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new CurrentUserService.UserView(principal.id(), principal.username(), principal.email());
    }
}
