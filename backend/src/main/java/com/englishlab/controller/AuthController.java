package com.englishlab.controller;

import com.englishlab.dto.AuthDtos.AuthResponse;
import com.englishlab.dto.AuthDtos.ForgotPasswordRequest;
import com.englishlab.dto.AuthDtos.ForgotPasswordSmsRequest;
import com.englishlab.dto.AuthDtos.GoogleSignInRequest;
import com.englishlab.dto.AuthDtos.GoogleSignInResult;
import com.englishlab.dto.AuthDtos.LoginRequest;
import com.englishlab.dto.AuthDtos.MessageResponse;
import com.englishlab.dto.AuthDtos.RegisterRequest;
import com.englishlab.dto.AuthDtos.ResendVerificationRequest;
import com.englishlab.dto.AuthDtos.ResetPasswordRequest;
import com.englishlab.dto.AuthDtos.ResetPasswordSmsRequest;
import com.englishlab.dto.AuthDtos.VerifyEmailRequest;
import com.englishlab.security.JwtService.AuthenticatedUser;
import com.englishlab.service.AuthService;
import com.englishlab.service.GoogleAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
    }

    /** 202: la cuenta queda pendiente de verificar; NO se devuelve JWT. */
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.register(request));
    }

    /** POST (no GET) para que los antivirus/prefetch de los clientes de correo no consuman el token. */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Registro/inicio de sesión con Google (ID token en {@code credential}). 200 con el mismo
     * {@code AuthResponse} que el login tradicional; 202 si Google no certificó el correo y la cuenta
     * queda pendiente del enlace de verificación.
     */
    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleSignInRequest request) {
        GoogleSignInResult result = googleAuthService.signIn(request.credential());
        if (result.session() != null) {
            return ResponseEntity.ok(result.session());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result.pending());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password/sms")
    public ResponseEntity<MessageResponse> forgotPasswordBySms(@Valid @RequestBody ForgotPasswordSmsRequest request) {
        return ResponseEntity.ok(authService.forgotPasswordBySms(request));
    }

    @PostMapping("/reset-password/sms")
    public ResponseEntity<MessageResponse> resetPasswordBySms(@Valid @RequestBody ResetPasswordSmsRequest request) {
        return ResponseEntity.ok(authService.resetPasswordBySms(request));
    }
}
