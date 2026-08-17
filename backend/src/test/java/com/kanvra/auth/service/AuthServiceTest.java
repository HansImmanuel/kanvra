package com.kanvra.auth.service;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.common.error.DuplicateEmailException;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service().register(new RegisterRequest("  Hans ", "A@B.C", "password123"));

        assertThat(user.getName()).isEqualTo("Hans");
        assertThat(user.getEmail()).isEqualTo("a@b.c");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$hashed");
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
}