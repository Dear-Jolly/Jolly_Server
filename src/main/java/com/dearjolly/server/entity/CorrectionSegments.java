package com.dearjolly.server.entity;

import com.dearjolly.server.entity.enums.CorrectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "CORRECTION_SEGMENTS")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class CorrectionSegments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_segments_id")
    private Long id;

    @ManyToOne
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
}
