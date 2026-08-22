package com.dearjolly.server.global.auth.jwt;

import com.dearjolly.server.domain.user.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long accessExpireMillis;
    private final long refreshExpireMillis;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpireMillis = properties.accessExpireMillis();
        this.refreshExpireMillis = properties.refreshExpireMillis();
    }

    public String createAccessToken(Long userId, Role role) {
        return createToken(userId, role, accessExpireMillis);
    }

    public String createRefreshToken(Long userId, Role role) {
        return createToken(userId, role, refreshExpireMillis);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    // 인증 필터는 요청마다 사용자와 권한을 함께 본다. 두 번 파싱하지 않도록 한 번에 꺼낸다.
    public JwtPayload payloadOf(String token) {
        Claims claims = parse(token);
        return new JwtPayload(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get(ROLE_CLAIM, String.class))
        );
    }

    private String createToken(Long userId, Role role, long expireMillis) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }
}
