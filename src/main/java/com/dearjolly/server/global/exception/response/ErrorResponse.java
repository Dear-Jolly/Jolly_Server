package com.dearjolly.server.global.exception.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Schema(description = "모든 API 가 공유하는 실패 응답")
@Builder
@Getter
public class ErrorResponse {
    @Schema(description = "HTTP 상태 코드", example = "400")
    private final int status;

    @Schema(description = "{도메인}_{일련번호} 형식의 에러 코드", example = "COMMON_001")
    private final String code;

    @Schema(description = "유저에게 그대로 보여줘도 되는 문구", example = "잘못된 요청입니다.")
    private final String message;

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.builder()
                        .status(errorCode.getHttpStatus().value())
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.builder()
                        .status(errorCode.getHttpStatus().value())
                        .code(errorCode.getCode())
                        .message(message)
                        .build());
    }
}
