package com.dearjolly.server.global.ratelimit;

import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** API 전체와 사용자별 편지 작성 횟수를 고정 1분 창으로 제한한다. */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final AuthenticatedUserHolder authenticatedUserHolder;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${dearjolly.rate-limit.global-per-minute:120}")
    private int globalLimit = 120;

    @Value("${dearjolly.rate-limit.letter-per-user-per-minute:5}")
    private int letterPerUserLimit = 5;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientKey = clientKey(request);
        if (!allow("global:" + clientKey, globalLimit)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        if (isLetterCreate(request)) {
            Long userId = authenticatedUserHolder.get() == null ? null : authenticatedUserHolder.get().getId();
            if (userId != null && !allow("letter:" + userId, letterPerUserLimit)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        }
        return true;
    }

    private boolean isLetterCreate(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/letters".equals(request.getRequestURI());
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean allow(String key, int limit) {
        long window = Instant.now().getEpochSecond() / 60;
        Window counter = windows.computeIfAbsent(key, ignored -> new Window(window));
        return counter.tryAcquire(window, limit);
    }

    private static final class Window {
        private long window;
        private int count;

        private Window(long window) {
            this.window = window;
        }

        private synchronized boolean tryAcquire(long currentWindow, int limit) {
            if (window != currentWindow) {
                window = currentWindow;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
