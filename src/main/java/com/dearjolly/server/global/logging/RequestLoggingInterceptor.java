package com.dearjolly.server.global.logging;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingInterceptor implements HandlerInterceptor {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String START_NANOS_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".startNanos";

    private final AuthenticatedUserHolder authenticatedUserHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = UUID.randomUUID().toString();
        Users user = authenticatedUserHolder.get();
        String userId = user == null ? "anonymous" : String.valueOf(user.getId());
        String nickname = user == null ? "anonymous" : LogValueSanitizer.sanitize(user.getNickname());

        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        MDC.put("userId", userId);
        MDC.put("nickname", nickname);

        log.info(
                "business_request_started method={} path={} operation={} clientIp={}",
                request.getMethod(),
                LogValueSanitizer.sanitize(request.getRequestURI()),
                operationName(handler),
                LogValueSanitizer.sanitize(clientIp(request))
        );
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception
    ) {
        try {
            long elapsedMillis = elapsedMillis(request);
            String exceptionType = exception == null ? "none" : exception.getClass().getSimpleName();
            if (response.getStatus() >= 500) {
                log.error(
                        "business_request_completed method={} path={} status={} elapsedMs={} exception={}",
                        request.getMethod(), LogValueSanitizer.sanitize(request.getRequestURI()),
                        response.getStatus(), elapsedMillis, exceptionType
                );
            } else if (response.getStatus() >= 400) {
                log.warn(
                        "business_request_completed method={} path={} status={} elapsedMs={} exception={}",
                        request.getMethod(), LogValueSanitizer.sanitize(request.getRequestURI()),
                        response.getStatus(), elapsedMillis, exceptionType
                );
            } else {
                log.info(
                        "business_request_completed method={} path={} status={} elapsedMs={} exception={}",
                        request.getMethod(), LogValueSanitizer.sanitize(request.getRequestURI()),
                        response.getStatus(), elapsedMillis, exceptionType
                );
            }
        } finally {
            MDC.clear();
        }
    }

    private String operationName(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return "unknown";
        }
        return handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private long elapsedMillis(HttpServletRequest request) {
        Object startNanos = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (!(startNanos instanceof Long start)) {
            return -1;
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
