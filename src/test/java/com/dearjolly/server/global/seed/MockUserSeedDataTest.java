package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.feedback.entity.Feedbacks.MAX_TIP_COUNT;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.CONTENT_MAX_LENGTH;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.KOREAN_PATTERN;
import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

// 시드 데이터가 편지 작성 API 의 제약을 그대로 만족하는지 본다.
// 어긋나면 시드가 API 로는 만들 수 없는 편지를 DB 에 넣어 테스트를 헛돌게 한다.
class MockUserSeedDataTest {
    @DisplayName("편지 본문은 서로 달라야 시드를 다시 돌려도 늘어나지 않는다.")
    @Test
    void contentsAreUnique() {
        // when
        Set<String> contents = MockUserSeedData.LETTERS.stream()
                .map(MockLetterSeed::content)
                .collect(Collectors.toSet());

        // then
        assertThat(contents).hasSameSizeAs(MockUserSeedData.LETTERS);
    }

    @DisplayName("편지 본문은 편지 작성 API 의 길이·영문 제약을 지킨다.")
    @Test
    void contentsSatisfyLetterConstraints() {
        // when & then
        assertThat(MockUserSeedData.LETTERS).allSatisfy(seed -> {
            assertThat(seed.content()).isNotBlank();
            assertThat(seed.content().codePointCount(0, seed.content().length()))
                    .isLessThanOrEqualTo(CONTENT_MAX_LENGTH);
            assertThat(KOREAN_PATTERN.matcher(seed.content()).find()).isFalse();
        });
    }

    @DisplayName("피드백이 붙은 편지의 학습 팁은 최대 개수를 넘지 않는다.")
    @Test
    void tipsAreWithinLimit() {
        // when & then
        assertThat(MockUserSeedData.LETTERS)
                .filteredOn(MockLetterSeed::isFeedbackCompleted)
                .allSatisfy(seed -> assertThat(seed.tips()).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_TIP_COUNT));
    }

    @DisplayName("피드백이 붙은 편지의 우표는 우표 시드 이미지에 실제로 존재한다.")
    @Test
    void stampNamesExistInStampSeed() throws IOException {
        // given
        Set<String> seededStampNames = seededStampNames();

        // when & then
        assertThat(MockUserSeedData.LETTERS)
                .filteredOn(MockLetterSeed::isFeedbackCompleted)
                .allSatisfy(seed -> assertThat(seed.stampName())
                        .isNotEqualTo(DEFAULT_STAMP_NAME)
                        .isIn(seededStampNames));
    }

    private Set<String> seededStampNames() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:seed/stamps/*.png");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(fileName -> fileName != null)
                .map(fileName -> fileName.substring(0, fileName.lastIndexOf('.')))
                .collect(Collectors.toSet());
    }
}
