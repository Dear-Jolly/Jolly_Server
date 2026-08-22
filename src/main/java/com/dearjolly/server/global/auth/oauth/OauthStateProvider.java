package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.auth.jwt.JwtProperties;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// 서버가 여러 대로 뜨고 세션도 없으므로 state 를 저장해 두고 대조할 수 없다.
// 대신 서명과 만료를 실어 보내, 콜백으로 돌아온 값이 이 서비스가 방금 발급한 것인지만 확인한다.
@Component
public class OauthStateProvider {
    private static final long EXPIRE_MILLIS = 600_000L;

    private final SecretKey key;

    public OauthStateProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(OauthProvider provider) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(provider.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MILLIS))
                .signWith(key)
                .compact();
    }

    public void validate(OauthProvider provider, String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, "state 누락");
        }

        Claims claims = parse(state);
        if (!provider.name().equals(claims.getSubject())) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, "state provider 불일치");
        }
    }

    private Claims parse(String state) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(state)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, "state 검증 실패");
        }
    }
}
