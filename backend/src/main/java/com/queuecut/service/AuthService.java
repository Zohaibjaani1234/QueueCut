package com.queuecut.service;

import com.queuecut.dto.auth.ChangePasswordRequest;
import com.queuecut.dto.auth.LoginRequest;
import com.queuecut.dto.auth.LoginResponse;
import com.queuecut.entity.BarberAccount;
import com.queuecut.exception.InvalidPasswordChangeException;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final BarberAccountRepository barberAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final long expirationMs;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       BarberAccountRepository barberAccountRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.barberAccountRepository = barberAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.expirationMs = expirationMs;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        BarberAccount barber = barberAccountRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Barber account not found: " + request.getUsername()));

        String token = jwtTokenProvider.generateToken(barber.getUsername(), barber.getRole());

        return new LoginResponse(token, barber.getUsername(), barber.getFullName(), barber.getRole(), expirationMs);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        BarberAccount barber = barberAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Barber account not found: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), barber.getPasswordHash())) {
            throw new InvalidPasswordChangeException("Current password is incorrect.");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new InvalidPasswordChangeException("New password must be different from the current password.");
        }

        barber.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        barberAccountRepository.save(barber);
    }
}
