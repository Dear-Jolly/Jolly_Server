package com.dearjolly.server.global.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param appRedirectUri 인증을 마친 뒤 앱으로 돌아갈 딥링크 (예: {@code dearjolly://auth/callback})
 */
@ConfigurationProperties(prefix = "dearjolly.oauth")
public record OauthProperties(
        String appRedirectUri,
        Kakao kakao,
        Apple apple
) {
    /**
     * @param clientId Kakao 개발자 콘솔의 REST API 키
     * @param clientSecret 보안 설정에서 활성화한 경우에만 필요
     * @param adminKey unlink 용 어드민 키
     */
    public record Kakao(
            String clientId,
            String clientSecret,
            String redirectUri,
            String adminKey
    ) {
    }

    /**
     * @param clientId Services ID (aud 검증값)
     * @param privateKey client_secret 생성용 PKCS#8 PEM
     */
    public record Apple(
            String clientId,
            String teamId,
            String keyId,
            String privateKey,
            String redirectUri
    ) {
    }
}
