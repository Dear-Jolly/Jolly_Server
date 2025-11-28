package com.dearjolly.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FEEDBACKS")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Feedbacks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

    @OneToOne
    @JoinColumn(name = "letter_id", nullable = false)
    private Letters letter;

    @Lob
    @Column(name = "corrected_content", nullable = false)
    private String correctedContent;

    @Lob
    @Column(name = "tip")
    private String tip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "correctionSegments")
    private List<CorrectionSegments> correctionSegments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Feedbacks(
            Long id,
            Letters letter,
            String correctedContent,
            String tip,
            LocalDateTime createdAt,
            List<CorrectionSegments> correctionSegments
    ) {
        this.id = id;
        this.letter = letter;
        this.correctedContent = correctedContent;
        this.tip = tip;
        this.createdAt = createdAt;
        this.correctionSegments = correctionSegments;
    }

    public void addCorrectionSegments(CorrectionSegments correctionSegment) {
        this.correctionSegments.add(correctionSegment);
    }
}
