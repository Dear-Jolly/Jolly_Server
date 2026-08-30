package com.dearjolly.server.global.config;

import com.dearjolly.server.global.exception.response.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 실패 응답 예시를 각 응답이 실제로 내려주는 에러 코드로 바꾼다.
 * <p>
 * ErrorResponse 의 필드 예시는 하나뿐이라, 손대지 않으면 404 응답에도 400 · COMMON_001 예시가 붙는다.
 * 응답 설명에 적어 둔 {@code `CODE`} 를 읽어 ErrorCode 에서 실제 상태·문구를 가져온다.
 */
@Component
public class ErrorExampleCustomizer implements OperationCustomizer {
    private static final Pattern ERROR_CODE = Pattern.compile("`([A-Z]+_\\d+)`");

    private static final Map<String, ErrorCode> BY_CODE = new LinkedHashMap<>();

    static {
        for (ErrorCode errorCode : ErrorCode.values()) {
            BY_CODE.put(errorCode.getCode(), errorCode);
        }
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getResponses() == null) {
            return operation;
        }
        operation.getResponses().computeIfAbsent("429", ignored -> tooManyRequestsResponse());
        operation.getResponses().forEach(this::applyExamples);
        return operation;
    }

    private ApiResponse tooManyRequestsResponse() {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                .example(bodyOf(ErrorCode.TOO_MANY_REQUESTS));
        return new ApiResponse()
                .description("`COMMON_004` 분당 요청 횟수 초과")
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private void applyExamples(String statusCode, ApiResponse response) {
        Content content = response.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }
        List<ErrorCode> errorCodes = errorCodesOf(statusCode, response.getDescription());
        if (errorCodes.isEmpty()) {
            return;
        }
        content.values().forEach(mediaType -> setExamples(mediaType, errorCodes));
    }

    // 설명에 적힌 코드 중 이 응답의 상태 코드와 실제로 맞는 것만 쓴다.
    // 어긋난 코드가 예시로 나가면 문서가 거짓말을 하게 된다.
    private List<ErrorCode> errorCodesOf(String statusCode, String description) {
        if (description == null) {
            return List.of();
        }
        List<ErrorCode> found = new ArrayList<>();
        Matcher matcher = ERROR_CODE.matcher(description);
        while (matcher.find()) {
            ErrorCode errorCode = BY_CODE.get(matcher.group(1));
            if (errorCode != null && String.valueOf(errorCode.getHttpStatus().value()).equals(statusCode)) {
                found.add(errorCode);
            }
        }
        return found;
    }

    // 코드가 여럿이면 Swagger 가 드롭다운으로 골라 보여준다.
    private void setExamples(MediaType mediaType, List<ErrorCode> errorCodes) {
        if (errorCodes.size() == 1) {
            mediaType.setExample(bodyOf(errorCodes.getFirst()));
            return;
        }
        for (ErrorCode errorCode : errorCodes) {
            mediaType.addExamples(errorCode.getCode(), new Example()
                    .summary(errorCode.getCode() + " — " + errorCode.getMessage())
                    .value(bodyOf(errorCode)));
        }
    }

    private Map<String, Object> bodyOf(ErrorCode errorCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", errorCode.getHttpStatus().value());
        body.put("code", errorCode.getCode());
        body.put("message", errorCode.getMessage());
        return body;
    }
}
