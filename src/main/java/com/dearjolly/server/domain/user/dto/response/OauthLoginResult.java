package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;

/**
 * 콜백 처리 결과. 컨트롤러가 이 값을 딥링크 쿼리 파라미터로 펼쳐 앱에 전달한다.
 * JSON 으로 나가지 않으므로 응답 DTO 라기보다 서비스 반환 값에 가깝다.
 */
public record OauthLoginResult(
        String accessToken,
        String refreshToken,
        Long userId,
        boolean isNewUser,
        boolean termsAgreed,
        boolean nicknameRegistered
) {
    public static OauthLoginResult of(Users user, String accessToken, String refreshToken, boolean isNewUser, boolean termsAgreed) {
        return new OauthLoginResult(
                accessToken,
                refreshToken,
                user.getId(),
                isNewUser,
                termsAgreed,
                user.isNicknameRegistered()
        );
    }
}
