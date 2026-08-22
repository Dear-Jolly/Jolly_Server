package com.dearjolly.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 엔티티 매핑이 Flyway 마이그레이션 스키마와 일치하는지 검증한다.
 *
 * <p>{@code V1__init.sql} (docs/ERD.md §7 의 DDL 을 그대로 옮긴 것)로 스키마를 만든 뒤
 * Hibernate {@code validate} 를 돌린다. 운영에서 뜨는 것과 같은 조건이므로, 엔티티에
 * 문서에 없는 컬럼이 생기거나 타입이 어긋나면 여기서 깨진다.
 */
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
