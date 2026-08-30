package com.dearjolly.server.global.exception.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    OAUTH_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_002", "소셜 로그인 인증에 실패했습니다."),
    OAUTH_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH_003", "소셜 로그인 서버와 통신하지 못했습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그인이 만료되었습니다. 다시 로그인해주세요."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_005", "유효하지 않은 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_006", "접근 권한이 없습니다."),
    WITHDRAWN_USER(HttpStatus.UNAUTHORIZED, "AUTH_007", "탈퇴한 계정입니다. 다시 로그인해주세요."),
    ADMIN_CREDENTIALS_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_008", "관리자 아이디 또는 비밀번호가 올바르지 않습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "USER_002", "필수 약관에 모두 동의해야 합니다."),
    NICKNAME_INVALID_CHARACTER(HttpStatus.BAD_REQUEST, "USER_003", "닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다."),
    NICKNAME_INVALID_LENGTH(HttpStatus.BAD_REQUEST, "USER_004", "닉네임은 1자 이상 20자 이하여야 합니다."),
    ONBOARDING_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "USER_005", "온보딩을 먼저 완료해야 합니다."),

    LETTER_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "LETTER_001", "편지 내용은 null일 수 없습니다."),
    LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "LETTER_002", "존재하지 않는 편지입니다."),
    LETTER_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "LETTER_003", "편지 내용은 500자를 초과할 수 없습니다."),
    LETTER_CONTENT_NOT_ENGLISH(HttpStatus.BAD_REQUEST, "LETTER_004", "편지는 영어로만 작성할 수 있습니다."),
    LETTER_WRITTEN_AT_INVALID(HttpStatus.BAD_REQUEST, "LETTER_005", "편지 작성 시각 정보가 올바르지 않습니다."),
    LETTER_FEEDBACK_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "LETTER_006", "이미 피드백이 완료된 편지입니다."),
    LETTER_FEEDBACK_IN_PROGRESS(HttpStatus.BAD_REQUEST, "LETTER_007", "피드백을 처리 중인 편지입니다."),

    APP_VERSION_INVALID(HttpStatus.BAD_REQUEST, "VERSION_001", "앱 버전은 x.y.z 형식이어야 합니다."),
    APP_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "VERSION_002", "해당 플랫폼의 최소 지원 버전이 설정되어 있지 않습니다."),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청입니다."),
    PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "요청하신 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_003", "지원하지 않는 요청 방식입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_004", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "일시적인 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
