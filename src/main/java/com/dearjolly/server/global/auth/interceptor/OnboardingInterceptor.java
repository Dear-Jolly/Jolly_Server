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
