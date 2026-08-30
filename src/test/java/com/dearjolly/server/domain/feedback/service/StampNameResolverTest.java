package com.dearjolly.server.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StampNameResolverTest {
    private static final List<String> CANDIDATES = List.of("꽃_장미", "친구", "친구_위로", "해_맑음");

    private final StampNameResolver stampNameResolver = new StampNameResolver();

    @DisplayName("후보와 정확히 같은 이름을 그대로 돌려준다.")
    @Test
    void resolveExactName() {
        // when
        String resolved = stampNameResolver.resolve("꽃_장미", CANDIDATES);

        // then
        assertThat(resolved).isEqualTo("꽃_장미");
    }

    @DisplayName("밑줄이 공백으로 바뀐 이름도 후보 이름으로 되돌린다.")
    @Test
    void resolveNameWithChangedSeparator() {
        // when
        String resolved = stampNameResolver.resolve("꽃 장미", CANDIDATES);

        // then
        assertThat(resolved).isEqualTo("꽃_장미");
    }

    @DisplayName("한쪽이 다른 쪽에 포함되는 후보끼리 섞이지 않는다.")
    @Test
    void doNotMatchByInclusion() {
        // when
        String resolved = stampNameResolver.resolve("친구", CANDIDATES);

        // then
        assertThat(resolved).isEqualTo("친구");
    }

    @DisplayName("후보에 없는 이름은 실제 응답값과 함께 실패한다.")
    @Test
    void failOnUnknownName() {
        // when & then
        assertThatThrownBy(() -> stampNameResolver.resolve("장미", CANDIDATES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("장미");
    }

    @DisplayName("우표를 고르지 않은 응답은 실패한다.")
    @Test
    void failOnMissingName() {
        // when & then
        assertThatThrownBy(() -> stampNameResolver.resolve(null, CANDIDATES))
                .isInstanceOf(IllegalStateException.class);
    }
}
