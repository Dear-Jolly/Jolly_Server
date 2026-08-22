package com.dearjolly.server;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway 가 만든 스키마와 JPA 엔티티 매핑이 어긋나지 않는지 실제 MySQL 로 확인한다.
 *
 * <p>나머지 테스트는 H2 + {@code ddl-auto: create-drop} 이라 엔티티로 스키마를 새로 만든다.
 * 그 경로에서는 {@code V1__init.sql} 이 한 번도 실행되지 않으므로, 마이그레이션과 엔티티가
 * 어긋나도 전부 초록불이 뜨고 운영의 {@code ddl-auto: validate} 에서 기동 실패로 처음 드러난다.
 * ERD 를 스키마의 정본으로 삼는다는 전제(기능명세 §4.3)를 지켜주는 것이 이 테스트다.
 */
@Testcontainers
@ActiveProfiles("migration")
@SpringBootTest
class SchemaMigrationTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("dearjolly")
            .withUsername("jolly")
            .withPassword("jolly");

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        // 운영과 동일하게 검증만 수행한다. 매핑이 어긋나면 컨텍스트 로딩 자체가 실패한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @DisplayName("V1 마이그레이션으로 만든 스키마가 엔티티 매핑을 그대로 통과한다")
    @Test
    void migrationMatchesEntities() {
        // 컨텍스트가 떴다는 것 자체가 validate 통과를 뜻한다. 테이블 존재까지 한 번 더 확인한다.
        List<?> tables = entityManager.createNativeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'dearjolly'")
                .getResultList();

        assertThat(tables.stream().map(String::valueOf).map(String::toLowerCase))
                .contains("users", "terms_agreements", "stamps", "letters",
                        "feedbacks", "correction_segments", "feedback_tips");
    }

    @DisplayName("초기 우표 마스터 4건이 파일 키로 적재된다")
    @Test
    void seedsStamps() {
        Object count = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM stamps").getSingleResult();
        assertThat(((Number) count).intValue()).isEqualTo(4);

        Object imageKey = entityManager.createNativeQuery(
                "SELECT image_key FROM stamps WHERE name = '장미'").getSingleResult();
        assertThat(String.valueOf(imageKey)).isEqualTo("stamps/flower_stamp.png");
    }

    @DisplayName("교정 조각 텍스트는 교정문 상한과 같은 1000자를 받아들인다")
    @Test
    void segmentTextHoldsFullCorrectedContent() {
        Object length = entityManager.createNativeQuery(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = 'dearjolly' AND table_name = 'correction_segments' "
                        + "AND column_name = 'corrected_text'").getSingleResult();

        assertThat(((Number) length).intValue()).isEqualTo(1000);
    }
}
