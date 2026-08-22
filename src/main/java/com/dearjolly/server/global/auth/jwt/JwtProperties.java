package com.dearjolly.server.global.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HS256 시크릿 (256bit 이상)
 * @param accessExpireMillis Access Token 만료 (기본 30분)
 * @param refreshExpireMillis Refresh Token 만료 (기본 14일)
 */
@ConfigurationProperties(prefix = "dearjolly.jwt")
public record JwtProperties(
        String secret,
        long accessExpireMillis,
        long refreshExpireMillis
) {
}
