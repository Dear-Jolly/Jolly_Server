package com.dearjolly.server.global.exception.handler;

import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;

import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn(
                "business_request_rejected code={} status={} message={}",
                e.getErrorCode().getCode(), e.getErrorCode().getHttpStatus().value(), sanitize(e.getMessage())
        );
        return ErrorResponse.toResponseEntity(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("request_validation_failed message={}", sanitize(errorMessage));
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST, errorMessage);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn("request_parameter_validation_failed message={}", sanitize(e.getMessage()));
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("request_parameter_missing parameter={} message={}", e.getParameterName(), sanitize(e.getMessage()));
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST, e.getParameterName() + " 파라미터는 필수입니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("request_parameter_type_mismatch parameter={} message={}", e.getName(), sanitize(e.getMessage()));
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST, "파라미터 타입이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseException(HttpMessageNotReadableException e) {
        log.warn("request_json_parse_failed causeType={}", e.getClass().getSimpleName());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST, "JSON 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("request_method_not_allowed method={} message={}", e.getMethod(), sanitize(e.getMessage()));
        return ErrorResponse.toResponseEntity(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("request_path_not_found path={}", sanitize(e.getResourcePath()));
        return ErrorResponse.toResponseEntity(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error(
                "unexpected_request_failure causeType={} causeMessage={}",
                e.getClass().getSimpleName(), sanitize(e.getMessage()), e
        );
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
