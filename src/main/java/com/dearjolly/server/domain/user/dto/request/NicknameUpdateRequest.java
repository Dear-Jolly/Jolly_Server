package com.dearjolly.server.domain.user.dto.request;

/**
 * 검증 어노테이션을 두지 않는다. 길이 위반(USER_004)과 문자 위반(USER_003)을
 * 다른 코드로 구분해야 하는데, Bean Validation 을 태우면 둘 다 COMMON_001 로 뭉개진다.
 * null·빈 값을 포함한 전 구간을 서비스에서 순서대로 검증한다 (기능명세 §3.2.2).
 */
public record NicknameUpdateRequest(
        String nickname
) {
}
