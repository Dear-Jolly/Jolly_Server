package com.dearjolly.server.global.seed;

import static com.dearjolly.server.global.version.constants.VersionValidationConstants.DEFAULT_MIN_SUPPORTED_VERSION;

import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppVersionSeedWriter {
    private final AppVersionRepository appVersionRepository;

    // 이미 있는 행은 손대지 않는다. 값의 주인은 관리자 API 이고, 시드는 빈 자리만 채운다.
    @Transactional
    public int write() {
        int created = 0;
        for (Platform platform : Platform.values()) {
            if (appVersionRepository.existsById(platform)) {
                continue;
            }
            appVersionRepository.save(AppVersions.create(platform, DEFAULT_MIN_SUPPORTED_VERSION));
            created++;
        }
        return created;
    }
}
