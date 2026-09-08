package com.queuecut.security;

import com.queuecut.entity.BarberAccount;
import com.queuecut.repository.BarberAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BarberUserDetailsService implements UserDetailsService {

    private final BarberAccountRepository barberAccountRepository;

    public BarberUserDetailsService(BarberAccountRepository barberAccountRepository) {
        this.barberAccountRepository = barberAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        BarberAccount barber = barberAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Barber account not found: " + username));

        return User.builder()
                .username(barber.getUsername())
                .password(barber.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(barber.getRole())))
                .build();
    }
}
