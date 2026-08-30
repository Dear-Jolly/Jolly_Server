package com.dearjolly.server.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("live")
class SpringAiLlmClientLiveTest {
    private static final List<String> STAMP_NAMES = List.of("친구", "영화", "맥주");

    @Autowired
    private LlmClient llmClient;

    @DisplayName("실제 OpenAI API가 구조화된 영어 교정 피드백을 반환한다.")
    @Test
    void requestActualOpenAiFeedback() {
        // given
        String content = "Yesterday I go to movie with my friend and we was very happy.";

        // when
        LlmFeedback feedback = llmClient.correct(content, STAMP_NAMES);

        // then
        assertThat(feedback.correctedContent())
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(1000)
                .isNotEqualTo(content);
        assertThat(feedback.tips()).hasSizeLessThanOrEqualTo(3);
        assertThat(feedback.tips()).allSatisfy(tip -> assertThat(tip).containsPattern("[가-힣]"));
        assertThat(feedback.stampName()).isIn(STAMP_NAMES);
        assertThat(feedback.model()).startsWith("gpt-4o-mini");
    }
}
