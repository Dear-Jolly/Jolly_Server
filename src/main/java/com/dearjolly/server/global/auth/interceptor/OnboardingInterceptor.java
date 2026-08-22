package com.dearjolly.server.global.auth.interceptor;

import com.dearjolly.server.domain.user.service.OnboardingChecker;
import com.dearjolly.server.global.auth.principal.AuthUser;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 온보딩(필수 약관 동의 + 닉네임 등록)을 마치지 않은 유저의 본 기능 API 접근을 막는다.
 * 이 가드 덕분에 편지 목록·홈 응답의 nickname 이 항상 non-null 임이 보장된다 (API명세 §2.6).
 */
@Component
@RequiredArgsConstructor
public class OnboardingInterceptor implements HandlerInterceptor {

    private final OnboardingChecker onboardingChecker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        if (!onboardingChecker.isOnboardingCompleted(authUser.userId())) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED);
        }
        return true;
    }
}
