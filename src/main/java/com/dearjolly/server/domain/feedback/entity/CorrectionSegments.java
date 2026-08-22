package com.dearjolly.server.domain.feedback.entity;

import static com.dearjolly.server.domain.feedback.enums.CorrectionType.MODIFIED;
import static com.dearjolly.server.domain.feedback.enums.CorrectionType.UNCHANGED;

import com.dearjolly.server.domain.feedback.enums.CorrectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원문을 sequence 순으로 잘게 나눈 조각. 순서대로 이어붙이면 원문과 교정문이
 * 그대로 복원되므로 앱은 인덱스 계산 없이 순차 렌더링만 한다.
 * 저장 후에는 바뀌지 않으므로 수정 메서드를 두지 않는다.
 */
@Entity
@Table(
        name = "CORRECTION_SEGMENTS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_segments_order",
                columnNames = {"feedback_id", "sequence"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorrectionSegments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_segment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedbacks feedback;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "original_text", nullable = false, length = 500)
    private String originalText;

    /** 삭제 제안이면 빈 문자열이다. null 이 아니다. */
    @Column(name = "corrected_text", nullable = false, length = 500)
    private String correctedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 20)
    private CorrectionType correctionType;

    // ========= 생성 메서드 =========
    public static CorrectionSegments create(Feedbacks feedback, int sequence, String originalText, String correctedText) {
        CorrectionSegments segment = new CorrectionSegments(feedback, sequence, originalText, correctedText);
        feedback.addCorrectionSegment(segment);
        return segment;
    }

    // ========= 비즈니스 로직 메서드 =========
    public boolean isModified() {
        return this.correctionType == MODIFIED;
    }

    // ========= 생성자 =========
    private CorrectionSegments(Feedbacks feedback, int sequence, String originalText, String correctedText) {
        validateSequence(sequence);
        validateText(originalText, "원본 텍스트 조각");
        validateText(correctedText, "교정 텍스트 조각");
        this.feedback = feedback;
        this.sequence = sequence;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.correctionType = Objects.equals(originalText, correctedText) ? UNCHANGED : MODIFIED;
    }

    // ========= 검증 메서드 =========
    private void validateSequence(int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("조각 순서는 1부터 시작합니다.");
        }
    }

    private void validateText(String text, String fieldName) {
        if (text == null) {
            throw new IllegalArgumentException(fieldName + "은(는) null일 수 없습니다.");
        }
    }
}
