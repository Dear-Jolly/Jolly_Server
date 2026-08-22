package com.dearjolly.server.global.version;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공지사항 · 개인정보처리방침 · 이용약관은 별도 API 없이 이 URL 을 웹뷰로 연다.
 */
@ConfigurationProperties(prefix = "dearjolly.policy")
public record PolicyProperties(
        String privacyPolicyUrl,
        String termsOfServiceUrl,
        String noticeUrl
) {
}
