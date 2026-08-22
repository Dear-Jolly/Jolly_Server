package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;

/**
 * @param oauthId provider 가 발급한 회원 식별자 (Kakao id, Apple sub)
 * @param email provider 가 주지 않으면 null. 서버가 대체 주소를 지어내지 않는다.
 * @param oauthRefreshToken provider 의 refresh token. Apple revoke 에 쓰며 Kakao 는 null 일 수 있다. /
 */
public record OauthUserInfo(
        OauthProvider provider,
        String oauthId,
        String email,
        String oauthRefreshToken
) {
}
