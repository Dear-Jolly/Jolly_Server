package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;

public interface OauthClient {
    OauthProvider supports();

    String buildAuthorizationUri(String state);

    /** @param idToken Apple 이 form_post 로 함께 보내주는 id_token. Kakao 는 null. / */
    OauthUserInfo exchange(String code, String idToken);

    void unlink(String oauthId, String oauthRefreshToken);
}
