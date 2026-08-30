package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;
import static com.dearjolly.server.domain.letter.constants.StampConstants.FAILED_STAMP_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("live")
class SpringAiLlmClientLiveTest {
    // 후보 개수와 이름 형태가 응답 스키마의 enum 크기를 그대로 결정한다.
    // 짧은 목록으로만 검증하면 운영에서 스키마가 거부되는 것을 잡지 못한다.
    private static final List<String> STAMP_NAMES = seedStampNames();

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

    private static List<String> seedStampNames() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:seed/stamps/*.png");
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .map(fileName -> fileName.substring(0, fileName.lastIndexOf('.')))
                    .filter(name -> !DEFAULT_STAMP_NAME.equals(name) && !FAILED_STAMP_NAME.equals(name))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("우표 시드 이미지를 읽지 못했다.", e);
        }
    }
}
