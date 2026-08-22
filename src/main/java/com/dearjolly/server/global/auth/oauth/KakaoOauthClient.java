package com.dearjolly.server.global.auth.oauth;

import static com.dearjolly.server.domain.user.enums.OauthProvider.KAKAO;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 (authorization code 플로우).
 *
 * <p>로그인 페이지 → 코드 발급 → 토큰 교환 → 유저 정보 조회를 전부 서버가 수행한다.
 * 참고: <a href="https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api">카카오 로그인 REST API</a>
 */
@Slf4j
@Component
public class KakaoOauthClient implements OauthClient {

    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

    private final RestClient restClient;
    private final OauthProperties.Kakao properties;

    public KakaoOauthClient(RestClient restClient, OauthProperties oauthProperties) {
        this.restClient = restClient;
        this.properties = oauthProperties.kakao();
    }

    @Override
    public OauthProvider supports() {
        return KAKAO;
    }

    @Override
    public String buildAuthorizationUri(String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", properties.clientId());
        params.put("redirect_uri", properties.redirectUri());
        params.put("response_type", "code");
        params.put("state", state);
        return AUTHORIZE_URI + "?" + toQueryString(params);
    }

    @Override
    public OauthUserInfo exchange(String code, String idToken) {
        KakaoTokenResponse token = requestToken(code);
        KakaoUserResponse user = requestUserInfo(token.accessToken());
        return new OauthUserInfo(KAKAO, String.valueOf(user.id()), user.email(), token.refreshToken());
    }

    /**
     * 연결 해제 실패는 로그만 남긴다. 사용자가 탈퇴하지 못하는 상태에 갇히지 않게 하기 위함이다
     * (기능명세 §3.1.3).
     */
    @Override
    public void unlink(String oauthId, String oauthRefreshToken) {
        if (properties.adminKey() == null || properties.adminKey().isBlank()) {
            log.warn("카카오 admin key 가 없어 unlink 를 건너뛴다. oauthId={}", oauthId);
            return;
        }
        try {
            restClient.post()
                    .uri(UNLINK_URI)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toQueryString(Map.of("target_id_type", "user_id", "target_id", oauthId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("카카오 unlink 실패. 탈퇴는 계속 진행한다. oauthId={}", oauthId, e);
        }
    }

    private KakaoTokenResponse requestToken(String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", properties.clientId());
        form.put("redirect_uri", properties.redirectUri());
        form.put("code", code);
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.put("client_secret", properties.clientSecret());
        }

        KakaoTokenResponse response = post(TOKEN_URI, form, KakaoTokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        return response;
    }

    private KakaoUserResponse requestUserInfo(String accessToken) {
        KakaoUserResponse response;
        try {
            response = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
                    })
                    .body(KakaoUserResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
        }

        if (response == null || response.id() == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        return response;
    }

    private <T> T post(String uri, Map<String, String> form, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toQueryString(form))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
                    })
                    .body(responseType);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }

    private String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), UTF_8))
                .collect(Collectors.joining("&"));
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
        String email() {
            return kakaoAccount == null ? null : kakaoAccount.email();
        }
    }

    private record KakaoAccount(String email) {
    }
}
