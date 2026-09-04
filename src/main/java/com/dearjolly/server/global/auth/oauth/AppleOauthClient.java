package com.dearjolly.server.global.auth.oauth;

import static com.dearjolly.server.domain.user.enums.OauthProvider.APPLE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AppleOauthClient implements OauthClient {
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String AUTHORIZE_URI = ISSUER + "/auth/authorize";
    private static final String JWK_URI = ISSUER + "/auth/keys";
    private static final String TOKEN_URI = ISSUER + "/auth/token";
    private static final String REVOKE_URI = ISSUER + "/auth/revoke";
    private static final Duration JWK_CACHE_TTL = Duration.ofHours(1);
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final OauthProperties.Apple properties;
    private final AtomicReference<CachedJwkSet> jwkCache = new AtomicReference<>();

    public AppleOauthClient(RestClient restClient, OauthProperties oauthProperties) {
        this.restClient = restClient;
        this.properties = oauthProperties.apple();
    }

    @Override
    public OauthProvider supports() {
        return APPLE;
    }

    @Override
    public String buildAuthorizationUri(String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", properties.clientId());
        params.put("redirect_uri", properties.redirectUri());
        params.put("response_type", "code id_token");
        params.put("scope", "name email");
        params.put("response_mode", "form_post");
        params.put("state", state);
        return AUTHORIZE_URI + "?" + toQueryString(params);
    }

    @Override
    public OauthUserInfo exchange(String code, String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        JWTClaimsSet claims = verifyIdToken(idToken);
        return new OauthUserInfo(APPLE, claims.getSubject(), extractEmail(claims), resolveRefreshToken(code));
    }

    // 회원 식별은 서명을 검증한 id_token 만으로 끝난다.
    // 여기서 받는 refresh token 은 탈퇴 시 Apple revoke 에만 쓰이므로 못 받아오더라도 로그인을 막지 않는다.
    // client_secret 설정이 비어 있거나 애플 토큰 엔드포인트가 흔들려도 사용자는 앱에 들어갈 수 있어야 한다.
    // 값이 없는 계정은 unlink 단계에서 revoke 를 건너뛴다.
    private String resolveRefreshToken(String code) {
        try {
            return requestToken(code).refreshToken();
        } catch (Exception e) {
            log.warn("애플 refresh token 을 받지 못했다. 로그인은 진행하되 이 계정은 탈퇴 시 revoke 를 건너뛴다.", e);
            return null;
        }
    }

    @Override
    public void unlink(String oauthId, String oauthRefreshToken) {
        if (oauthRefreshToken == null || oauthRefreshToken.isBlank()) {
            log.warn("Apple refresh token 이 없어 revoke 를 건너뛴다. oauthId={}", oauthId);
            return;
        }
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", properties.clientId());
            form.put("client_secret", createClientSecret());
            form.put("token", oauthRefreshToken);
            form.put("token_type_hint", "refresh_token");

            restClient.post()
                    .uri(REVOKE_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toQueryString(form))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Apple revoke 실패. 탈퇴는 계속 진행한다. oauthId={}", oauthId, e);
        }
    }

    private JWTClaimsSet verifyIdToken(String idToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            verifySignature(jwt);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            verifyClaims(claims);
            return claims;
        } catch (BusinessException e) {
            throw e;
        } catch (ParseException | JOSEException e) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
    }

    private void verifySignature(SignedJWT jwt) throws JOSEException {
        String kid = jwt.getHeader().getKeyID();
        JWK jwk = findKey(kid, false);
        if (jwk == null) {
            jwk = findKey(kid, true);
        }
        if (!(jwk instanceof RSAKey rsaKey) || !jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
    }

    private void verifyClaims(JWTClaimsSet claims) {
        boolean valid = ISSUER.equals(claims.getIssuer())
                && claims.getAudience() != null
                && claims.getAudience().contains(properties.clientId())
                && claims.getExpirationTime() != null
                && claims.getExpirationTime().after(new Date())
                && claims.getSubject() != null;
        if (!valid) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
    }

    private String extractEmail(JWTClaimsSet claims) {
        try {
            return claims.getStringClaim("email");
        } catch (ParseException e) {
            return null;
        }
    }

    private AppleTokenResponse requestToken(String code) {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", properties.clientId());
            form.put("client_secret", createClientSecret());
            form.put("code", code);
            form.put("grant_type", "authorization_code");
            form.put("redirect_uri", properties.redirectUri());

            AppleTokenResponse response = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toQueryString(form))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("애플 토큰 요청이 거절됐다. status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.warn("애플 토큰 요청 중 애플 서버 오류. status={}, body={}", res.getStatusCode(), readBody(res));
                        throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
                    })
                    .body(AppleTokenResponse.class);

            return response == null ? new AppleTokenResponse(null) : response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }

    private JWK findKey(String kid, boolean forceRefresh) {
        CachedJwkSet cached = jwkCache.get();
        if (forceRefresh || cached == null || cached.isExpired()) {
            cached = new CachedJwkSet(fetchJwkSet(), Instant.now());
            jwkCache.set(cached);
        }
        return cached.jwkSet().getKeyByKeyId(kid);
    }

    private JWKSet fetchJwkSet() {
        try {
            String body = restClient.get()
                    .uri(JWK_URI)
                    .retrieve()
                    .body(String.class);
            return JWKSet.parse(body);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }

    private String createClientSecret() {
        try {
            return signClientSecret();
        } catch (Exception e) {
            log.warn("애플 client_secret 생성에 실패했다. private key · key id · team id 조합을 확인한다. keyId={}, teamId={}",
                    properties.keyId(), properties.teamId(), e);
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
    }

    private String signClientSecret() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(
                properties.privateKey()
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "")
        );
        ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(properties.teamId())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(CLIENT_SECRET_TTL)))
                        .audience(ISSUER)
                        .subject(properties.clientId())
                        .build()
        );
        jwt.sign(new ECDSASigner(privateKey));
        return jwt.serialize();
    }

    private String readBody(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), UTF_8);
    }

    private String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), UTF_8))
                .collect(Collectors.joining("&"));
    }

    private record CachedJwkSet(JWKSet jwkSet, Instant fetchedAt) {
        boolean isExpired() {
            return fetchedAt.plus(JWK_CACHE_TTL).isBefore(Instant.now());
        }
    }

    private record AppleTokenResponse(
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }
}
