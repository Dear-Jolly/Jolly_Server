package com.dearjolly.server.domain.letter.dto.response;

public record HomeGetResponse(
        String nickname,
        int totalStampCount
) {
    public static HomeGetResponse of(String nickname, long totalStampCount) {
        return new HomeGetResponse(nickname, (int) totalStampCount);
    }
}
