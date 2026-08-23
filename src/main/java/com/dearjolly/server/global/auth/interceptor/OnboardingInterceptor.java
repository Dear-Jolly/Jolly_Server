package com.dearjolly.server.global.auth.interceptor;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.service.OnboardingChecker;
import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class OnboardingInterceptor implements HandlerInterceptor {
    private final OnboardingChecker onboardingChecker;
    private final AuthenticatedUserHolder authenticatedUserHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Users user = authenticatedUserHolder.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        if (!onboardingChecker.isOnboardingCompleted(user)) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED);
        }
        return true;
    }
}
