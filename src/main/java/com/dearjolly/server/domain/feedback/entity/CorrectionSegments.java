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

    @Column(name = "original_text", nullable = false, length = 1000)
    private String originalText;

    @Column(name = "corrected_text", nullable = false, length = 1000)
    private String correctedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 20)
    private CorrectionType correctionType;

    public static CorrectionSegments create(Feedbacks feedback, int sequence, String originalText, String correctedText) {
        CorrectionSegments segment = new CorrectionSegments(feedback, sequence, originalText, correctedText);
        feedback.addCorrectionSegment(segment);
        return segment;
    }

    public boolean isModified() {
        return this.correctionType == MODIFIED;
    }

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
