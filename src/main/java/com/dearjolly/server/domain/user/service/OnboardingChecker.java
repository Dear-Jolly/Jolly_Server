package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.entity.Users;

public interface OnboardingChecker {
    boolean isOnboardingCompleted(Users user);
}
