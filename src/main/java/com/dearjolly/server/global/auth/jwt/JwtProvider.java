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

/**
 * Access / Refresh 토큰 발급과 파싱을 담당한다.
 * Access Token claims 는 sub(userId), role, iat, exp 다 (기능명세 §3.1.2).
 */
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

    /**
     * 서명·만료를 검증하고 claims 를 돌려준다. 유효하지 않으면 JwtException 이 올라간다.
     */
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

    public Role getRole(String token) {
        return Role.valueOf(parse(token).get(ROLE_CLAIM, String.class));
    }

    private String createToken(Long userId, Role role, long expireMillis) {
        Date now = new Date();
        return Jwts.builder()
                // iat 는 초 단위라 같은 초에 두 번 발급하면 토큰 문자열이 같아진다.
                // 그러면 Refresh Token 을 회전해도 이전 토큰이 그대로 유효해지므로 jti 로 매번 다르게 만든다.
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }
}
