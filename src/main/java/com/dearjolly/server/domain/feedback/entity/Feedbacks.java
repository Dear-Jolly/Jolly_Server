package com.dearjolly.server.domain.feedback.entity;

import com.dearjolly.server.domain.letter.entity.Letters;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FEEDBACKS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedbacks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

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
    }

    // ========= 생성 메서드 =========
    public static Feedbacks create(Letters letter, String correctedContent, String tip) {
        Feedbacks feedback = new Feedbacks(letter, correctedContent, tip);
        letter.registerFeedback(feedback);
        return feedback;
    }

    // ========= 연관관계 메서드 =========
    public void addCorrectionSegment(CorrectionSegments segment) {
        this.correctionSegments.add(segment);
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateFeedback(String correctedContent, String tip) {
        this.correctedContent = correctedContent;
        this.tip = tip;
    }
}
