package com.queuecut.service;

import com.queuecut.dto.auth.LoginRequest;
import com.queuecut.dto.auth.LoginResponse;
import com.queuecut.entity.BarberAccount;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final BarberAccountRepository barberAccountRepository;
    private final long expirationMs;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       BarberAccountRepository barberAccountRepository,
                       @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.barberAccountRepository = barberAccountRepository;
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
}
