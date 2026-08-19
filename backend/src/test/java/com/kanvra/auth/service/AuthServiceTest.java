package com.kanvra.auth.service;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.model.RefreshToken;
import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.RefreshTokenRepository;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.DuplicateEmailException;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private KanvraProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KanvraProperties();
    }

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenRepository, properties);
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(any(AuthenticatedUser.class))).thenReturn("access");
        when(jwtService.createRefreshToken(any(AuthenticatedUser.class), any(String.class))).thenReturn("refresh");
        when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthSession session = service().register(new RegisterRequest("  Hans ", "A@B.C", "password123"));

        assertThat(session.user().getName()).isEqualTo("Hans");
        assertThat(session.user().getEmail()).isEqualTo("a@b.c");
        assertThat(session.user().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(session.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(true);

        assertThatThrownBy(() -> service().register(new RegisterRequest("Hans", "a@b.c", "password123")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User();
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service().login(new LoginRequest("a@b.c", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRotatesAndRevokesOldToken() {
        User user = new User();
        RefreshToken oldRow = activeRefreshToken(7L, "old-jti");
        when(jwtService.extractJti("old-token", JwtTokenType.REFRESH)).thenReturn("old-jti");
        when(jwtService.parse("old-token", JwtTokenType.REFRESH)).thenReturn(new AuthenticatedUser(7L, "H", "h@x.com"));
        when(refreshTokenRepository.findByJti("old-jti")).thenReturn(Optional.of(oldRow));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(any(AuthenticatedUser.class))).thenReturn("new-access");
        when(jwtService.createRefreshToken(any(AuthenticatedUser.class), any(String.class))).thenReturn("new-refresh");
        when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> {
                    RefreshToken row = invocation.getArgument(0);
                    row.setId(500L); // simulate the DB-assigned id so replaced_by is wired
                    return row;
                });

        AuthSession session = service().refresh("old-token");

        assertThat(session.refreshToken()).isEqualTo("new-refresh");
        assertThat(oldRow.getRevokedAt()).isNotNull();
        assertThat(oldRow.getReplacedBy()).isNotNull();
        verify(refreshTokenRepository).save(oldRow);
    }

    @Test
    void refreshRejectsUnknownJti() {
        when(jwtService.extractJti("nope", JwtTokenType.REFRESH)).thenReturn("missing-jti");
        when(jwtService.parse("nope", JwtTokenType.REFRESH)).thenReturn(new AuthenticatedUser(1L, "A", "a@x.com"));
        when(refreshTokenRepository.findByJti("missing-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().refresh("nope"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        User user = new User();
        RefreshToken expired = activeRefreshToken(1L, "expired-jti");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(jwtService.extractJti("tok", JwtTokenType.REFRESH)).thenReturn("expired-jti");
        when(jwtService.parse("tok", JwtTokenType.REFRESH)).thenReturn(new AuthenticatedUser(1L, "A", "a@x.com"));
        when(refreshTokenRepository.findByJti("expired-jti")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service().refresh("tok"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshReuseOfRevokedTokenRevokesWholeFamily() {
        RefreshToken revoked = activeRefreshToken(9L, "stolen-jti");
        revoked.setRevokedAt(Instant.now());
        when(jwtService.extractJti("stolen", JwtTokenType.REFRESH)).thenReturn("stolen-jti");
        when(jwtService.parse("stolen", JwtTokenType.REFRESH)).thenReturn(new AuthenticatedUser(9L, "V", "v@x.com"));
        when(refreshTokenRepository.findByJti("stolen-jti")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service().refresh("stolen"))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository).revokeAllActiveForUser(any(Long.class), any(Instant.class));
    }

    private RefreshToken activeRefreshToken(Long userId, String jti) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setJti(jti);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}

