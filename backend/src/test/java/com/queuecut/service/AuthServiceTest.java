package com.queuecut.service;

import com.queuecut.dto.auth.LoginRequest;
import com.queuecut.dto.auth.LoginResponse;
import com.queuecut.entity.BarberAccount;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private BarberAccountRepository barberAccountRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtTokenProvider, barberAccountRepository, 86400000L);
    }

    @Test
    @DisplayName("login: returns JWT and user details upon valid credentials")
    void login_Success() {
        BarberAccount barber = new BarberAccount();
        barber.setUsername("arslan");
        barber.setFullName("Muhammad Arslan");
        barber.setRole("ROLE_BARBER");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("arslan", "password"));
        when(barberAccountRepository.findByUsername("arslan")).thenReturn(Optional.of(barber));
        when(jwtTokenProvider.generateToken("arslan", "ROLE_BARBER")).thenReturn("mock-jwt-token");

        LoginRequest request = new LoginRequest("arslan", "password");
        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getUsername()).isEqualTo("arslan");
        assertThat(response.getFullName()).isEqualTo("Muhammad Arslan");
        assertThat(response.getRole()).isEqualTo("ROLE_BARBER");
    }

    @Test
    @DisplayName("login: propagates BadCredentialsException when authentication fails")
    void login_BadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("arslan", "wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
