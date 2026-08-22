package com.dearjolly.server.domain.user.service;

public interface OnboardingChecker {
    boolean isOnboardingCompleted(Long userId);
}
