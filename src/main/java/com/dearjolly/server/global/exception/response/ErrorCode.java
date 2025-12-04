package com.dearjolly.server.global.exception.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // global
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G500", "서버 내부에 문제가 발생했습니다."),
    DATABASE_SAVE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G500", "데이터베이스 저장에 실패하였습니다."),
    DATABASE_READ_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G500", "데이터베이스 조회에 실패하였습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "G405", "허용되지 않는 메서드입니다."),

    // user
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U404", "존재하지 않는 사용자입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "U400", "올바르지 않은 값 또는 형식입니다."),

    // resource
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "R404", "요청한 리소스가 존재하지 않습니다."),
    RESOURCE_DUPLICATED(HttpStatus.CONFLICT, "R409", "중복해서 저장할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
