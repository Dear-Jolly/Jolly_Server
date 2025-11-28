package com.dearjolly.server.entity;

import com.dearjolly.server.entity.enums.CorrectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CORRECTION_SEGMENTS")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class CorrectionSegments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_segment_id")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedbacks feedback;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "original_text", nullable = false, length = 500)
    private String originalText;

    @Column(name = "corrected_text", nullable = false, length = 500)
    private String correctedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CorrectionType type;

    private CorrectionSegments(Feedbacks feedback, Integer sequence, String originalText, String correctedText, CorrectionType type) {
        this.feedback = feedback;
        this.sequence = sequence;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.type = type;
    }

    public static CorrectionSegments create(Feedbacks feedback, Integer sequence, String originalText, String correctedText,
                                            CorrectionType type) {
        CorrectionSegments correctionSegment = new CorrectionSegments(feedback, sequence, originalText, correctedText, type);
        feedback.addCorrectionSegment(correctionSegment);
        return correctionSegment;
    }

}
