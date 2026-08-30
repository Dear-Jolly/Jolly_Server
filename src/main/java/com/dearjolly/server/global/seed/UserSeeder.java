package com.dearjolly.server.global.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSeeder implements ApplicationRunner {
    private final UserSeedProperties userSeedProperties;
    private final UserSeedWriter userSeedWriter;

    @Override
    public void run(ApplicationArguments args) {
        if (!userSeedProperties.enabled()) {
            log.info("시드 User 가 비활성화되어 있어 건너뛴다.");
            return;
        }
        try {
            Long userId = userSeedWriter.write(userSeedProperties);
            log.info("시드 User 완료. userId={}", userId);
        } catch (Exception e) {
            // 시드 실패로 기동을 막으면 무중단 배포가 통째로 멈춘다. 로그만 남기고 기동은 이어간다.
            log.error("시드 User 생성에 실패했다.", e);
        }
    }
}
