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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Entity
@Table(name = "CORRECTION_SEGMENTS")
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

    @Column(name = "corrected_text", nullable = false, length = 500)
    private String correctedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CorrectionType type;

    private CorrectionSegments(Feedbacks feedback, int sequence, String originalText, String correctedText) {
        this.feedback = feedback;
        this.sequence = sequence;
        this.originalText = originalText;
        this.correctedText = correctedText;
        if(Objects.equals(originalText, correctedText)){
            this.type = UNCHANGED;
        } else {
            this.type = MODIFIED;
        }
    }

    // ========= 생성 메서드 =========
    public static CorrectionSegments create(Feedbacks feedback, Integer sequence, String originalText, String correctedText) {
        CorrectionSegments segment = new CorrectionSegments(feedback, sequence, originalText, correctedText);
        feedback.addCorrectionSegment(segment);
        return segment;
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateCorrection(String correctedText, CorrectionType type) {
        this.correctedText = correctedText;
        this.type = type;
    }

    public boolean isModifiedType() {
        return this.type == MODIFIED;
    }
}
