package com.dearjolly.server.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorrectionDifferTest {
    private final CorrectionDiffer correctionDiffer = new CorrectionDiffer();

    @DisplayName("바뀐 단어만 MODIFIED 조각으로 묶고 나머지는 UNCHANGED 조각으로 남긴다.")
    @Test
    void diff() {
        // given
        String original = "It really touched me and make me so happy.";
        String corrected = "It really touched me and made me so happy.";

        // when
        List<CorrectionPair> pairs = correctionDiffer.diff(original, corrected);

        // then
        assertThat(pairs).containsExactly(
                new CorrectionPair("It really touched me and ", "It really touched me and "),
                new CorrectionPair("make", "made"),
                new CorrectionPair(" me so happy.", " me so happy.")
        );
    }

    @DisplayName("교정할 것이 없으면 UNCHANGED 조각 하나만 만든다.")
    @Test
    void diffWithoutCorrection() {
        // given
        String content = "I got flowers from a friend today.";

        // when
        List<CorrectionPair> pairs = correctionDiffer.diff(content, content);

        // then
        assertThat(pairs).containsExactly(new CorrectionPair(content, content));
    }

    @DisplayName("단어가 삭제되면 correctedText 가 빈 문자열인 조각이 된다.")
    @Test
    void diffWithDeletion() {
        // when
        List<CorrectionPair> pairs = correctionDiffer.diff("I kept to look at them", "I kept at them");

        // then
        assertThat(pairs).contains(new CorrectionPair("to look ", ""));
    }

    @DisplayName("원본 조각과 교정 조각을 이어붙이면 각각 원문과 교정문이 된다.")
    @Test
    void diffKeepsInvariant() {
        // given
        String original = "I kept to look at them and smiling to myself, which make me happy.";
        String corrected = "I kept looking at them and smiling to myself, that made me so happy.";

        // when
        List<CorrectionPair> pairs = correctionDiffer.diff(original, corrected);

        // then
        assertThat(correctionDiffer.isValid(pairs, original, corrected)).isTrue();
        assertThat(pairs.stream().map(CorrectionPair::originalText).reduce("", String::concat))
                .isEqualTo(original);
        assertThat(pairs.stream().map(CorrectionPair::correctedText).reduce("", String::concat))
                .isEqualTo(corrected);
    }

    @DisplayName("조각 목록이 원문·교정문과 어긋나면 불변식 검사에 실패한다.")
    @Test
    void isValidFalse() {
        // given
        List<CorrectionPair> pairs = List.of(new CorrectionPair("hello", "hi"));

        // when
        boolean valid = correctionDiffer.isValid(pairs, "hello world", "hi");

        // then
        assertThat(valid).isFalse();
    }
}
