package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설정 화면에 표시할 계정 정보")
public record UserGetResponse(

        @Schema(description = "유저 닉네임. 온보딩 전에는 null 이다", example = "ilovesally")
        String nickname,

        @Schema(description = "로그인 수단", requiredMode = Schema.RequiredMode.REQUIRED)
        OauthProvider provider,

        @Schema(description = "소셜 계정 이메일. 애플에서 제공을 거부하면 null 이다", example = "kakao_user@email.com")
        String email,

        @Schema(description = "마케팅 수신 동의 여부. 동의 이력이 없으면 false 다", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean marketingAgreed
) {
    public static UserGetResponse of(Users user, boolean marketingAgreed) {
        return new UserGetResponse(
                user.getNickname(),
                user.getOauthProvider(),
                user.getEmail(),
                marketingAgreed
        );
    }
}
