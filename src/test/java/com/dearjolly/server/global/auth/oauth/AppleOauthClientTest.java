package com.dearjolly.server.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 애플 로그인에서 회원 식별은 서명을 검증한 id_token 만으로 끝난다.
 * 토큰 엔드포인트 호출은 탈퇴 시 revoke 에 쓸 refresh token 을 받으려는 부수 작업이므로
 * 그 단계가 무너져도 로그인은 통과해야 한다.
 */
class AppleOauthClientTest {
    private static final String CLIENT_ID = "com.dearjolly.auth";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String JWK_URI = ISSUER + "/auth/keys";
    private static final String TOKEN_URI = ISSUER + "/auth/token";

    private RSAKey signingKey;
    private RestClient restClient;
    private MockRestServiceServer server;
    private AppleOauthClient client;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        restClient = builder.build();

        client = new AppleOauthClient(restClient, properties());
    }

    @DisplayName("애플 토큰 엔드포인트가 죽어도 id_token 이 유효하면 로그인은 통과한다")
    @Test
    void exchangeSucceedsWhenTokenEndpointFails() {
        expectJwkSet();
        server.expect(ExpectedCount.manyTimes(), requestTo(TOKEN_URI))
                .andRespond(withServerError());

        OauthUserInfo userInfo = client.exchange("any-code", signedIdToken("apple-sub-1", "user@privaterelay.appleid.com"));

        assertThat(userInfo.provider()).isEqualTo(OauthProvider.APPLE);
        assertThat(userInfo.oauthId()).isEqualTo("apple-sub-1");
        assertThat(userInfo.email()).isEqualTo("user@privaterelay.appleid.com");
        assertThat(userInfo.oauthRefreshToken())
                .as("refresh token 을 못 받으면 null 이지만 로그인 자체는 막지 않는다")
                .isNull();
    }

    @DisplayName("client_secret 설정이 비어 있어도 로그인은 통과한다")
    @Test
    void exchangeSucceedsWhenClientSecretIsUnconfigured() {
        expectJwkSet();

        AppleOauthClient unconfigured = new AppleOauthClient(restClient, propertiesWithoutPrivateKey());

        OauthUserInfo userInfo = unconfigured.exchange("any-code", signedIdToken("apple-sub-2", null));

        assertThat(userInfo.oauthId()).isEqualTo("apple-sub-2");
        assertThat(userInfo.oauthRefreshToken()).isNull();
    }

    @DisplayName("애플이 이메일을 주지 않아도 sub 로 로그인이 성립한다")
    @Test
    void exchangeAcceptsMissingEmail() {
        expectJwkSet();
        server.expect(ExpectedCount.manyTimes(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"refresh_token\":\"apple-refresh\"}", MediaType.APPLICATION_JSON));

        OauthUserInfo userInfo = client.exchange("any-code", signedIdToken("apple-sub-3", null));

        assertThat(userInfo.email()).isNull();
        assertThat(userInfo.oauthRefreshToken()).isEqualTo("apple-refresh");
    }

    @DisplayName("id_token 이 없으면 인증 실패다")
    @Test
    void exchangeRejectsBlankIdToken() {
        assertThatThrownBy(() -> client.exchange("any-code", " "))
                .isInstanceOf(BusinessException.class);
    }

    @DisplayName("다른 서비스용으로 발급된 id_token 은 거부한다")
    @Test
    void exchangeRejectsForeignAudience() {
        expectJwkSet();

        String foreign = signedIdToken("apple-sub-4", null, "com.someone.else", ISSUER);

        assertThatThrownBy(() -> client.exchange("any-code", foreign))
                .isInstanceOf(BusinessException.class);
    }

    @DisplayName("만료된 id_token 은 거부한다")
    @Test
    void exchangeRejectsExpiredIdToken() {
        expectJwkSet();

        String expired = signedIdToken(
                "apple-sub-5", null, CLIENT_ID, ISSUER, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> client.exchange("any-code", expired))
                .isInstanceOf(BusinessException.class);
    }

    private void expectJwkSet() {
        server.expect(ExpectedCount.manyTimes(), requestTo(JWK_URI))
                .andRespond(withSuccess(
                        new JWKSet(List.of(signingKey.toPublicJWK())).toString(),
                        MediaType.APPLICATION_JSON));
    }

    private String signedIdToken(String subject, String email) {
        return signedIdToken(subject, email, CLIENT_ID, ISSUER);
    }

    private String signedIdToken(String subject, String email, String audience, String issuer) {
        return signedIdToken(subject, email, audience, issuer, Instant.now().plusSeconds(600));
    }

    private String signedIdToken(
            String subject, String email, String audience, String issuer, Instant expiresAt) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .audience(audience)
                    .issueTime(new Date())
                    .expirationTime(Date.from(expiresAt));
            if (email != null) {
                claims.claim("email", email);
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OauthProperties properties() {
        return new OauthProperties(
                "dearjolly://auth/callback",
                new OauthProperties.Kakao("kakao", "", "http://localhost/kakao", ""),
                new OauthProperties.Apple(CLIENT_ID, "TEAMID", "KEYID", validPrivateKeyPem(), "http://localhost/apple"));
    }

    private OauthProperties propertiesWithoutPrivateKey() {
        return new OauthProperties(
                "dearjolly://auth/callback",
                new OauthProperties.Kakao("kakao", "", "http://localhost/kakao", ""),
                new OauthProperties.Apple(CLIENT_ID, "TEAMID", "KEYID", "", "http://localhost/apple"));
    }

    // 실제 서명까지 가 봐야 client_secret 경로가 도는지 확인할 수 있어 EC 키를 즉석에서 만든다.
    private String validPrivateKeyPem() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
            generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + java.util.Base64.getMimeEncoder().encodeToString(encoded)
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
