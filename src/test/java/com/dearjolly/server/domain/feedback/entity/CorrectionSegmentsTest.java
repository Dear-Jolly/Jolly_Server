package com.dearjolly.server.domain.feedback.entity;

import static com.dearjolly.server.domain.feedback.enums.CorrectionType.MODIFIED;
import static com.dearjolly.server.domain.feedback.enums.CorrectionType.UNCHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class CorrectionSegmentsTest {

    @Test
    void punctuationOnlyChangeIsNotMarkedAsCorrection() {
        CorrectionSegments segment = CorrectionSegments.create(
                mock(Feedbacks.class), 1, "name", "name,"
        );

        assertThat(segment.getCorrectionType()).isEqualTo(UNCHANGED);
    }

    @Test
    void wordChangeRemainsMarkedAsCorrection() {
        CorrectionSegments segment = CorrectionSegments.create(
                mock(Feedbacks.class), 1, "name", "names,"
        );

        assertThat(segment.getCorrectionType()).isEqualTo(MODIFIED);
    }
}
