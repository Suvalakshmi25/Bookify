package com.bookify.service;

import com.bookify.dto.Dtos.*;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.ProviderProfileRepository;
import com.bookify.repository.RefreshTokenRepository;
import com.bookify.repository.UserRepository;
import com.bookify.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private static final String SESSION_OVER = "Your session has expired. Please log in again.";

    private final UserRepository users;
    private final ProviderProfileRepository providers;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final long refreshDays;

    public AuthService(UserRepository users, ProviderProfileRepository providers, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtService jwt, @Value("${bookify.jwt.refresh-days}") long refreshDays) {
        this.users = users;
        this.providers = providers;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        if (r.role() == Role.ADMIN) throw ApiException.badRequest("Admin accounts can't be created here");
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("That email is already registered");

        User u = new User();
        u.setFullName(r.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        users.save(u);

        if (r.role() == Role.PROVIDER) {
            ProviderProfile p = new ProviderProfile();
            p.setUser(u);
            p.setCategory(r.category() == null || r.category().isBlank() ? "General" : r.category().trim());
            p.setBio(r.bio());
            providers.save(p);
        }
        return issue(u);
    }

    @Transactional
    public AuthResponse login(LoginRequest r) {
        // same message for "no such user" and "wrong password" so the API doesn't reveal which emails exist
        User u = users.findByEmailIgnoreCase(r.email().trim())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Wrong email or password"));
        if (!u.isEnabled()) throw ApiException.forbidden("This account has been disabled");
        return issue(u);
    }

    /** Refresh tokens are single-use: the old one is deleted and a new pair is issued (rotation). */
    @Transactional
    public AuthResponse refresh(String token) {
        RefreshToken rt = refreshTokens.findByToken(token).orElseThrow(() -> ApiException.unauthorized(SESSION_OVER));
        refreshTokens.delete(rt);
        if (rt.getExpiresAt().isBefore(LocalDateTime.now()) || !rt.getUser().isEnabled())
            throw ApiException.unauthorized(SESSION_OVER);
        return issue(rt.getUser());
    }

    @Transactional
    public void logout(String token) {
        refreshTokens.findByToken(token).ifPresent(refreshTokens::delete);
    }

    private AuthResponse issue(User u) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(u);
        rt.setExpiresAt(LocalDateTime.now().plusDays(refreshDays));
        refreshTokens.save(rt);
        return new AuthResponse(jwt.generateAccessToken(u), rt.getToken(),
                new UserInfo(u.getId(), u.getFullName(), u.getEmail(), u.getRole()));
    }
}
