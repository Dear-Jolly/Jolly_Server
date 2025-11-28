package com.dearjolly.server.entity;

import com.dearjolly.server.entity.enums.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
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

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CorrectionSegments> correctionSegments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private Feedbacks(Letters letter, String correctedContent, String tip) {
        this.letter = letter;
        this.correctedContent = correctedContent;
        this.tip = tip;
        this.correctionSegments = new ArrayList<>();
    }

    public static Feedbacks create(Letters letter, String correctedContent, String tip) {
        Feedbacks feedback = new Feedbacks(letter, correctedContent, tip);
        letter.setFeedback(feedback);
        return feedback;
    }

    public void addCorrectionSegment(CorrectionSegments segment) {
        this.correctionSegments.add(segment);
        if (segment.getFeedback() != this) {
            segment.setFeedback(this);
        }
    }
}
