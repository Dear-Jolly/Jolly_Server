package com.dearjolly.server.global.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(SeedOrder.MOCK_USER)
@RequiredArgsConstructor
public class MockUserSeeder implements ApplicationRunner {
    private final MockUserSeedProperties mockUserSeedProperties;
    private final MockUserSeedWriter mockUserSeedWriter;

    @Override
    public void run(ApplicationArguments args) {
        if (!mockUserSeedProperties.enabled()) {
            log.info("목 사용자 시드가 비활성화되어 있어 건너뛴다.");
            return;
        }
        try {
            MockUserSeedResult result = mockUserSeedWriter.write(
                    mockUserSeedProperties, MockUserSeedData.LETTERS);
            log.info("목 사용자 시드 완료. userId={}, 편지 추가 {}건, 그중 피드백 완료 {}건",
                    result.userId(), result.createdLetters(), result.completedLetters());
        } catch (Exception e) {
            // 우표 시드와 같은 이유로 기동은 막지 않는다. 테스트 데이터가 없다고 서버가 못 뜰 이유는 없다.
            log.error("목 사용자 시드에 실패했다.", e);
        }
    }
}
