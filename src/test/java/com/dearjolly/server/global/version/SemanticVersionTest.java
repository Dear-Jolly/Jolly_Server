package com.dearjolly.server.global.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticVersionTest {
    @DisplayName("x.y.z 를 major, minor, patch 로 나눠 읽는다.")
    @Test
    void parse() {
        // when
        SemanticVersion version = SemanticVersion.parse("1.2.3");

        // then
        assertThat(version).isEqualTo(new SemanticVersion(1, 2, 3));
    }

    @DisplayName("major 가 다르면 minor · patch 는 보지 않는다.")
    @ParameterizedTest(name = "{0} < {1} 은 {2}")
    @CsvSource({
            "1.9.9, 2.0.0, true",
            "2.0.0, 1.9.9, false",
            "10.0.0, 9.9.9, false"
    })
    void compareMajorFirst(String left, String right, boolean older) {
        assertThat(SemanticVersion.parse(left).isOlderThan(SemanticVersion.parse(right))).isEqualTo(older);
    }

    @DisplayName("major 가 같으면 minor 를, minor 까지 같으면 patch 를 비교한다.")
    @ParameterizedTest(name = "{0} < {1} 은 {2}")
    @CsvSource({
            "1.1.9, 1.2.0, true",
            "1.2.0, 1.1.9, false",
            "1.2.3, 1.2.4, true",
            "1.2.4, 1.2.3, false",
            "1.2.3, 1.2.3, false",
            "1.2.10, 1.2.9, false"
    })
    void compareMinorThenPatch(String left, String right, boolean older) {
        assertThat(SemanticVersion.parse(left).isOlderThan(SemanticVersion.parse(right))).isEqualTo(older);
    }

    @DisplayName("x.y.z 형식이 아니면 VERSION_001 로 거절한다.")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "1", "1.2", "1.2.3.4", "1.2.x", "v1.2.3", "1.2.-3", "1.2.3 "})
    void parseInvalid(String value) {
        assertThatThrownBy(() -> SemanticVersion.parse(value))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APP_VERSION_INVALID);
    }
}
