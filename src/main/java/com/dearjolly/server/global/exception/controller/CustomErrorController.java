package com.dearjolly.server.global.exception.controller;

import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서블릿 컨테이너가 내부적으로 포워딩하는 경로다. 앱이 호출하는 API 가 아니므로 OpenAPI 문서에서 숨긴다. */
@Hidden
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // 404 Not Found (존재하지 않는 URL)
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.PATH_NOT_FOUND);
            }

            // 405 Method Not Allowed (잘못된 HTTP 메서드)
            if (statusCode == HttpStatus.METHOD_NOT_ALLOWED.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.METHOD_NOT_ALLOWED);
            }

            // 401 / 403 (Security 필터 단계에서 걸린 요청)
            if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.ACCESS_TOKEN_INVALID);
            }
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.ACCESS_DENIED);
            }
        }

        // 그 외 알 수 없는 에러
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
