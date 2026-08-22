package com.dearjolly.server.domain.user.service;

/**
 * 온보딩 완료 여부 판별을 인터페이스로 분리한다.
 * global 의 인터셉터가 user 서비스 구현체에 직접 의존하지 않게 하기 위함이다.
 */
public interface OnboardingChecker {

    /** 필수 약관 2건 동의 + 닉네임 등록이 모두 끝났는지 (ERD 불변식 A15) */
    boolean isOnboardingCompleted(Long userId);
}
