package com.dearjolly.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:migrationcheck;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class EntityMappingTest {
    @Test
    @DisplayName("엔티티 매핑이 Flyway 마이그레이션 스키마와 일치한다")
    void 엔티티_매핑이_마이그레이션_스키마와_일치한다() {
    }
}
