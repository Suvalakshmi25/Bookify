package com.bookify;

import com.bookify.entity.Role;
import com.bookify.entity.User;
import com.bookify.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setEmail("a@b.dev");
        u.setRole(Role.PROVIDER);
        return u;
    }

    @Test
    void roundTripKeepsSubjectAndRole() {
        JwtService jwt = new JwtService(SECRET, 15);
        Claims c = jwt.parse(jwt.generateAccessToken(user()));
        assertThat(c.getSubject()).isEqualTo("a@b.dev");
        assertThat(c.get("role", String.class)).isEqualTo("PROVIDER");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = new JwtService(SECRET, 15).generateAccessToken(user());
        JwtService other = new JwtService("a-completely-different-secret-a-completely-different", 15);
        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1);
        String token = jwt.generateAccessToken(user());
        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
    }
}
