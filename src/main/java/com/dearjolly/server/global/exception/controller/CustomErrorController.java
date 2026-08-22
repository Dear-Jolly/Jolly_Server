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

@Hidden
@RestController
public class CustomErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.PATH_NOT_FOUND);
            }

            if (statusCode == HttpStatus.METHOD_NOT_ALLOWED.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.METHOD_NOT_ALLOWED);
            }

            if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.ACCESS_TOKEN_INVALID);
            }
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return ErrorResponse.toResponseEntity(ErrorCode.ACCESS_DENIED);
            }
        }

        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
