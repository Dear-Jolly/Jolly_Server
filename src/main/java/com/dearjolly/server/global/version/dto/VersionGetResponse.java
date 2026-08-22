package com.dearjolly.server.global.version.dto;

import com.dearjolly.server.global.version.VersionProperties;
import com.dearjolly.server.global.version.enums.Platform;

/** @param forceUpdate latest 와 minSupported 가 같다는 것은 그 아래 버전을 더 받아주지 않겠다는 뜻이다. 앱은 자기 버전과 minSupportedVersion 을 직접 비교하고, 이 값은 보조 신호로 쓴다. / */
public record VersionGetResponse(
        String latestVersion,
        String minSupportedVersion,
        boolean forceUpdate,
        String privacyPolicyUrl,
        String termsOfServiceUrl,
        String noticeUrl
) {
    public static VersionGetResponse of(VersionProperties properties, Platform platform) {
        String latest = properties.latestFor(platform);
        String minSupported = properties.minSupportedFor(platform);
        return new VersionGetResponse(
                latest,
                minSupported,
                latest.equals(minSupported),
                properties.privacyPolicyUrl(),
                properties.termsOfServiceUrl(),
                properties.noticeUrl()
        );
    }
}
