package com.dearjolly.server.global.version.service;

import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.version.PolicyProperties;
import com.dearjolly.server.global.version.SemanticVersion;
import com.dearjolly.server.global.version.dto.request.VersionUpdateRequest;
import com.dearjolly.server.global.version.dto.response.VersionGetResponse;
import com.dearjolly.server.global.version.dto.response.VersionUpdateResponse;
import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionService {
    private final AppVersionRepository appVersionRepository;
    private final PolicyProperties policyProperties;

    public VersionGetResponse getVersion(Platform platform, String appVersion) {
        String minSupportedVersion = findByPlatform(platform).getMinSupportedVersion();
        boolean forceUpdate = SemanticVersion.parse(appVersion)
                .isOlderThan(SemanticVersion.parse(minSupportedVersion));

        return VersionGetResponse.of(minSupportedVersion, forceUpdate, policyProperties);
    }

    @Transactional
    public VersionUpdateResponse updateMinSupportedVersion(Platform platform, VersionUpdateRequest request) {
        AppVersions appVersion = findByPlatform(platform);
        appVersion.updateMinSupportedVersion(request.minSupportedVersion());

        return VersionUpdateResponse.from(appVersion);
    }

    // 행은 기동할 때 시더가 플랫폼마다 하나씩 채운다. 비어 있다면 시드가 돌지 않은 것이다.
    private AppVersions findByPlatform(Platform platform) {
        return appVersionRepository.findById(platform)
                .orElseThrow(() -> new BusinessException(ErrorCode.APP_VERSION_NOT_FOUND, "platform=" + platform));
    }
}
