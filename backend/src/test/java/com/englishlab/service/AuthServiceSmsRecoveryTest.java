package com.englishlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishlab.config.AppConfig.AuthProperties;
import com.englishlab.dto.AuthDtos.ForgotPasswordSmsRequest;
import com.englishlab.dto.AuthDtos.MessageResponse;
import com.englishlab.dto.AuthDtos.ResetPasswordSmsRequest;
import com.englishlab.exception.AppExceptions.BadRequestException;
import com.englishlab.model.entity.User;
import com.englishlab.repository.PasswordResetTokenRepository;
import com.englishlab.repository.RevokedTokenRepository;
import com.englishlab.repository.RoleRepository;
import com.englishlab.repository.UserRepository;
import com.englishlab.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceSmsRecoveryTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RevokedTokenRepository revokedTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock NotificationService notificationService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock TwilioVerifyService twilioVerifyService;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                roleRepository,
                revokedTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtService,
                new AuthProperties(false, false),
                notificationService,
                emailVerificationService,
                twilioVerifyService);

        user = new User();
        user.setId(4L);
        user.setEmail("student1@englishlab.com");
        user.setPhone("+529511234567");
        user.setPasswordHash("old-hash");
    }

    @Test
    void forgotPasswordBySms_registeredPhone_sendsCodeAndReturnsGenericMessage() {
        when(userRepository.findByPhone("+529511234567")).thenReturn(Optional.of(user));

        MessageResponse response = authService.forgotPasswordBySms(new ForgotPasswordSmsRequest("+52 951 123 4567"));

        assertThat(response.message()).isEqualTo(AuthService.FORGOT_SMS_MESSAGE);
        verify(twilioVerifyService).ensureConfigured();
        verify(twilioVerifyService).sendSmsCode("+529511234567");
    }

    @Test
    void forgotPasswordBySms_unknownPhone_doesNotSendAndReturnsSameGenericMessage() {
        when(userRepository.findByPhone("+529510000000")).thenReturn(Optional.empty());

        MessageResponse response = authService.forgotPasswordBySms(new ForgotPasswordSmsRequest("+529510000000"));

        assertThat(response.message()).isEqualTo(AuthService.FORGOT_SMS_MESSAGE);
        verify(twilioVerifyService).ensureConfigured();
        verify(twilioVerifyService, never()).sendSmsCode(any());
    }

    @Test
    void forgotPasswordBySms_invalidPhone_throwsBadRequest() {
        assertThatThrownBy(() -> authService.forgotPasswordBySms(new ForgotPasswordSmsRequest("9511234567")))
                .isInstanceOf(BadRequestException.class);
        verify(twilioVerifyService, never()).sendSmsCode(any());
    }

    @Test
    void resetPasswordBySms_validCode_updatesPasswordHash() {
        when(userRepository.findByPhone("+529511234567")).thenReturn(Optional.of(user));
        when(twilioVerifyService.checkSmsCode("+529511234567", "123456")).thenReturn(true);
        when(passwordEncoder.encode("NuevaClave#1")).thenReturn("new-bcrypt-hash");

        MessageResponse response = authService.resetPasswordBySms(
                new ResetPasswordSmsRequest("+52 951 123 4567", "123456", "NuevaClave#1"));

        assertThat(response.message()).contains("actualizada");
        assertThat(user.getPasswordHash()).isEqualTo("new-bcrypt-hash");
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordBySms_invalidOrExpiredCode_throwsBadRequestWithoutUpdatingPassword() {
        when(userRepository.findByPhone("+529511234567")).thenReturn(Optional.of(user));
        when(twilioVerifyService.checkSmsCode("+529511234567", "999999")).thenReturn(false);

        assertThatThrownBy(() -> authService.resetPasswordBySms(
                new ResetPasswordSmsRequest("+529511234567", "999999", "NuevaClave#1")))
                .isInstanceOf(BadRequestException.class);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordBySms_unknownPhone_throwsBadRequestWithoutCallingCheck() {
        when(userRepository.findByPhone("+529510000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPasswordBySms(
                new ResetPasswordSmsRequest("+529510000000", "123456", "NuevaClave#1")))
                .isInstanceOf(BadRequestException.class);
        verify(twilioVerifyService, never()).checkSmsCode(any(), any());
        verify(userRepository, never()).save(any());
    }
}
