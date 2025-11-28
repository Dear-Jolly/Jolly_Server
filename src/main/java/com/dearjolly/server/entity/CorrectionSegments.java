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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedbacks feedback;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Lob
    @Column(name = "original_text", nullable = false)
    private String originalText;

    @Lob
    @Column(name = "corrected_text", nullable = false)
    private String correctedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CorrectionType type;

    public CorrectionSegments(
            Long id,
            Feedbacks feedback,
            Integer sequence,
            String originalText,
            String correctedText,
            CorrectionType type
    ) {
        this.id = id;
        this.feedback = feedback;
        this.sequence = sequence;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.type = type;
        feedback.getCorrectionSegments().add(this);
    }
}
