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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "FEEDBACKS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedbacks {
    public static final int MAX_TIP_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letter_id", nullable = false, unique = true)
    private Letters letter;

    @Column(name = "corrected_content", nullable = false, length = 1000)
    private String correctedContent;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<CorrectionSegments> correctionSegments = new ArrayList<>();

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<FeedbackTips> tips = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Feedbacks create(Letters letter, String correctedContent, String model) {
        Feedbacks feedback = new Feedbacks(letter, correctedContent, model);
        letter.registerFeedback(feedback);
        return feedback;
    }

    public void addCorrectionSegment(CorrectionSegments segment) {
        this.correctionSegments.add(segment);
    }

    public void addTip(FeedbackTips tip) {
        validateTipCount();
        this.tips.add(tip);
    }

    public boolean hasTips() {
        return !this.tips.isEmpty();
    }

    private Feedbacks(Letters letter, String correctedContent, String model) {
        validateCorrectedContent(correctedContent);
        validateModel(model);
        this.letter = letter;
        this.correctedContent = correctedContent;
        this.model = model;
    }

    private void validateCorrectedContent(String correctedContent) {
        if (correctedContent == null || correctedContent.isBlank()) {
            throw new IllegalArgumentException("교정문은 필수입니다.");
        }
    }

    private void validateModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LLM 모델 ID는 필수입니다.");
        }
    }

    private void validateTipCount() {
        if (this.tips.size() >= MAX_TIP_COUNT) {
            throw new IllegalArgumentException("학습 팁은 최대 " + MAX_TIP_COUNT + "개입니다.");
        }
    }
}
