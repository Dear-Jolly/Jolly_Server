package com.dearjolly.server.global.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppVersionSeeder implements ApplicationRunner {
    private final AppVersionSeedProperties appVersionSeedProperties;
    private final AppVersionSeedWriter appVersionSeedWriter;

    @Override
    public void run(ApplicationArguments args) {
        if (!appVersionSeedProperties.enabled()) {
            log.info("앱 버전 시드가 비활성화되어 있어 건너뛴다.");
            return;
        }
        try {
            int created = appVersionSeedWriter.write();
            log.info("앱 버전 시드 완료. 신규 {}건", created);
        } catch (Exception e) {
            // 우표 시드와 같은 이유로 기동은 막지 않는다.
            log.error("앱 버전 시드에 실패했다.", e);
        }
    }
}
