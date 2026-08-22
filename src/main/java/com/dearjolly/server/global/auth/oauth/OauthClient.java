package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;

/**
 * 백엔드가 authorization code 플로우 전 구간을 담당한다.
 * 앱은 {@code GET /api/v1/auth/{provider}} 로 이동하기만 하면 되고,
 * 로그인 페이지 요청 → 코드 발급 → 토큰 교환 → 유저 정보 조회는 서버가 처리한다.
 */
public interface OauthClient {

    OauthProvider supports();

    /** provider 의 로그인 페이지 URL. 앱을 여기로 302 리다이렉트한다. */
    String buildAuthorizationUri(String state);

    /**
     * 콜백으로 받은 authorization code 를 토큰으로 교환하고 회원 정보를 조회한다.
     *
     * @param idToken Apple 이 form_post 로 함께 보내주는 id_token. Kakao 는 null.
     */
    OauthUserInfo exchange(String code, String idToken);

    /** 회원탈퇴 시 소셜 연결을 해제한다. 실패해도 예외를 밖으로 던지지 않는다. */
    void unlink(String oauthId, String oauthRefreshToken);
}
