package com.dearjolly.server.domain.letter.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 헤더 응답")
public record HomeGetResponse(

        @Schema(description = "유저 닉네임. 온보딩 가드 덕분에 항상 값이 있다", example = "Sally",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(description = "모은 우표 총 개수 (피드백이 완료된 편지 수)", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int totalStampCount
) {
    public static HomeGetResponse of(String nickname, long totalStampCount) {
        return new HomeGetResponse(nickname, (int) totalStampCount);
    }
}
