package com.dearjolly.server.domain.user.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.auth.oauth.OauthStateProvider;
import com.dearjolly.server.support.ApiTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 애플 심사 리뷰어는 대부분 '이메일 가리기' 를 골라 @privaterelay.appleid.com 주소로 들어온다.
 * 그 주소로 가입 → 온보딩 → 재로그인 → 탈퇴 → 재가입 까지 실제로 돌려본다.
 *
 * 애플 서버만 흉내 내고, 그 뒤 서버 로직과 DB 는 전부 실제로 동작시킨다.
 */
@Import(AppleLoginPrivateRelayApiTest.AppleStubConfig.class)
class AppleLoginPrivateRelayApiTest extends ApiTestSupport {
    static final String CLIENT_ID = "com.dearjolly.auth";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String PRIVATE_RELAY_EMAIL = "kx7q9v2m3n@privaterelay.appleid.com";
    private static final String APPLE_SUB = "001234.reviewer-apple-sub.0000";

    @Autowired
    private OauthStateProvider oauthStateProvider;

    // client_secret 서명까지 실제로 돌려야 refresh token 저장 경로를 확인할 수 있다.
    @DynamicPropertySource
    static void appleProperties(DynamicPropertyRegistry registry) {
        registry.add("dearjolly.oauth.apple.client-id", () -> CLIENT_ID);
        registry.add("dearjolly.oauth.apple.team-id", () -> "TEAMID1234");
        registry.add("dearjolly.oauth.apple.key-id", () -> "KEYID12345");
        registry.add("dearjolly.oauth.apple.private-key", AppleStubConfig::privateKeyPem);
        registry.add("dearjolly.oauth.apple.redirect-uri",
                () -> "https://dearjolly.test/api/v1/auth/apple/callback");
        registry.add("dearjolly.oauth.app-redirect-uri", () -> "dearjolly://auth/callback");
    }

    @DisplayName("private relay 주소로 가입부터 탈퇴까지 전 구간이 동작한다")
    @Test
    void privateRelaySignupThroughWithdrawal() {
        // 1. 최초 로그인 - 애플이 이메일을 딱 한 번 내려준다
        Map<String, String> first = 애플로_로그인한다(APPLE_SUB, PRIVATE_RELAY_EMAIL);

        assertThat(first.get("isNewUser")).as("첫 로그인은 신규 가입이다").isEqualTo("true");
        assertThat(first.get("termsAgreed")).isEqualTo("false");
        assertThat(first.get("nicknameRegistered")).isEqualTo("false");
        assertThat(first.get("accessToken")).isNotBlank();
        assertThat(first.get("refreshToken")).isNotBlank();

        Users saved = userRepository.findByOauthProviderAndOauthId(OauthProvider.APPLE, APPLE_SUB).orElseThrow();
        assertThat(saved.getEmail())
                .as("private relay 주소가 그대로 저장돼야 한다")
                .isEqualTo(PRIVATE_RELAY_EMAIL);
        assertThat(saved.getOauthRefreshToken())
                .as("탈퇴 시 revoke 에 쓸 애플 refresh token 이 저장돼야 한다")
                .isEqualTo("apple-refresh-token");

        String bearer = "Bearer " + first.get("accessToken");

        // 2. 온보딩 - 약관 동의
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .body(Map.of("agreements", List.of(
                        Map.of("type", "SERVICE", "agreed", true),
                        Map.of("type", "PRIVACY", "agreed", true),
                        Map.of("type", "MARKETING", "agreed", false))))
                .when().post("/api/v1/users/terms")
                .then().statusCode(HttpStatus.OK.value());

        // 3. 온보딩 - 닉네임 등록
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .body(Map.of("nickname", "jollyreviewer"))
                .when().patch("/api/v1/users/nickname")
                .then().statusCode(HttpStatus.OK.value());

        // 4. 설정 화면이 읽는 계정 정보
        given().header(HttpHeaders.AUTHORIZATION, bearer)
                .when().get("/api/v1/users")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("provider", org.hamcrest.Matchers.equalTo("APPLE"))
                .body("email", org.hamcrest.Matchers.equalTo(PRIVATE_RELAY_EMAIL))
                .body("nickname", org.hamcrest.Matchers.equalTo("jollyreviewer"));

        // 5. 재로그인 - 애플은 두 번째부터 email 클레임을 주지 않는다
        Map<String, String> second = 애플로_로그인한다(APPLE_SUB, null);

        assertThat(second.get("isNewUser")).as("같은 sub 면 기존 계정이다").isEqualTo("false");
        assertThat(second.get("termsAgreed")).isEqualTo("true");
        assertThat(second.get("nicknameRegistered")).isEqualTo("true");

        Users reloggedIn = userRepository.findByOauthProviderAndOauthId(OauthProvider.APPLE, APPLE_SUB).orElseThrow();
        assertThat(reloggedIn.getEmail())
                .as("이메일이 안 왔다고 기존 값을 지우면 안 된다")
                .isEqualTo(PRIVATE_RELAY_EMAIL);

        // 6. 탈퇴 - 앱 안에서 계정과 편지를 모두 지운다
        given().header(HttpHeaders.AUTHORIZATION, "Bearer " + second.get("accessToken"))
                .when().delete("/api/v1/users")
                .then().statusCode(HttpStatus.NO_CONTENT.value());

        Users withdrawn = userRepository.findByOauthProviderAndOauthId(OauthProvider.APPLE, APPLE_SUB).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).as("탈퇴 표시가 남는다").isTrue();
        assertThat(withdrawn.getOauthRefreshToken())
                .as("revoke 를 마쳤으므로 애플 토큰을 들고 있을 이유가 없다")
                .isNull();

        // 7. 탈퇴 후 재로그인 - 같은 애플 계정이라도 처음부터 다시 시작한다
        Map<String, String> rejoined = 애플로_로그인한다(APPLE_SUB, PRIVATE_RELAY_EMAIL);

        assertThat(rejoined.get("isNewUser")).as("탈퇴 후 재로그인은 신규 가입이다").isEqualTo("true");
        assertThat(rejoined.get("termsAgreed")).isEqualTo("false");
        assertThat(rejoined.get("nicknameRegistered")).isEqualTo("false");

        Users rejoinedUser =
                userRepository.findByOauthProviderAndOauthId(OauthProvider.APPLE, APPLE_SUB).orElseThrow();
        assertThat(rejoinedUser.isWithdrawn()).as("새로 만들어진 계정이다").isFalse();
        assertThat(rejoinedUser.getId())
                .as("탈퇴한 계정의 식별자를 비켜 주고 새 행이 생긴다")
                .isNotEqualTo(withdrawn.getId());
        assertThat(rejoinedUser.getNickname()).as("이전 닉네임을 물려받지 않는다").isNull();
    }

    @DisplayName("이메일 제공을 아예 거부해도 가입이 된다")
    @Test
    void signupWithoutEmail() {
        Map<String, String> result = 애플로_로그인한다("001234.no-email.0000", null);

        assertThat(result.get("isNewUser")).isEqualTo("true");
        assertThat(result.get("accessToken")).isNotBlank();

        Optional<Users> saved =
                userRepository.findByOauthProviderAndOauthId(OauthProvider.APPLE, "001234.no-email.0000");
        assertThat(saved).isPresent();
        assertThat(saved.get().getEmail()).isNull();
    }

    /** 앱이 실제로 받는 딥링크의 쿼리 파라미터를 그대로 돌려준다. */
    private Map<String, String> 애플로_로그인한다(String sub, String email) {
        String location = given().redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("code", "apple-authorization-code")
                .formParam("id_token", 애플이_서명한_idToken(sub, email))
                .formParam("state", oauthStateProvider.issue(OauthProvider.APPLE))
                .when().post("/api/v1/auth/apple/callback")
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .extract().header(HttpHeaders.LOCATION);

        assertThat(location)
                .as("앱이 등록한 커스텀 스킴으로 돌아가야 한다")
                .startsWith("dearjolly://auth/callback");

        return UriComponentsBuilder.fromUri(URI.create(location)).build()
                .getQueryParams()
                .toSingleValueMap();
    }

    private String 애플이_서명한_idToken(String sub, String email) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(sub)
                    .audience(CLIENT_ID)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)));
            if (email != null) {
                claims.claim("email", email);
                claims.claim("is_private_email", true);
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(AppleStubConfig.SIGNING_KEY.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(AppleStubConfig.SIGNING_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 애플 서버만 대신한다. 공개키 · 토큰 · revoke 세 엔드포인트가 전부다. */
    @TestConfiguration
    static class AppleStubConfig {
        static final RSAKey SIGNING_KEY = generateKey();

        @Bean
        @Primary
        RestClient appleStubRestClient() {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

            server.expect(ExpectedCount.manyTimes(), requestTo(ISSUER + "/auth/keys"))
                    .andRespond(withSuccess(
                            new JWKSet(List.of(SIGNING_KEY.toPublicJWK())).toString(), MediaType.APPLICATION_JSON));
            server.expect(ExpectedCount.manyTimes(), requestTo(ISSUER + "/auth/token"))
                    .andRespond(withSuccess(
                            "{\"refresh_token\":\"apple-refresh-token\"}", MediaType.APPLICATION_JSON));
            server.expect(ExpectedCount.manyTimes(), requestTo(ISSUER + "/auth/revoke"))
                    .andRespond(withSuccess());

            return builder.build();
        }

        /** 애플 client_secret 은 ES256 으로 서명한다. 실제 서명이 도는지 보려면 진짜 EC 키가 필요하다. */
        static String privateKeyPem() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
                generator.initialize(new ECGenParameterSpec("secp256r1"));
                byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
                return Base64.getEncoder().encodeToString(encoded);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private static RSAKey generateKey() {
            try {
                return new RSAKeyGenerator(2048).keyID("apple-stub-key").generate();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
