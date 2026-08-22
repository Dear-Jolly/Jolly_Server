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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @DisplayName("마이그레이션으로 만든 스키마가 엔티티 매핑을 그대로 통과한다")
    @Test
    void migrationMatchesEntities() {
        List<?> tables = entityManager.createNativeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'dearjolly'")
                .getResultList();

        assertThat(tables.stream().map(String::valueOf).map(String::toLowerCase))
                .contains("users", "terms_agreements", "stamps", "letters",
                        "feedbacks", "correction_segments", "feedback_tips", "app_versions");
    }

    @DisplayName("우표 마스터 테이블은 빈 상태로 생성된다")
    @Test
    void createsEmptyStampTable() {
        Object count = entityManager.createNativeQuery("SELECT COUNT(*) FROM stamps").getSingleResult();
        assertThat(((Number) count).intValue()).isZero();
    }

    @DisplayName("최소 지원 버전 테이블은 빈 상태로 생성된다. 행은 시더가 채운다")
    @Test
    void createsEmptyAppVersionTable() {
        Object count = entityManager.createNativeQuery("SELECT COUNT(*) FROM app_versions").getSingleResult();
        assertThat(((Number) count).intValue()).isZero();
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
