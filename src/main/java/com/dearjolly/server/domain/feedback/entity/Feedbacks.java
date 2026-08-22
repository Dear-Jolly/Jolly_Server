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

/**
 * 편지 한 통당 최대 1건이다. 피드백 저장·교정 조각·팁 저장·편지 상태 전이·우표 부여는
 * 하나의 트랜잭션에서 처리하므로 부분 저장 상태는 존재하지 않는다.
 */
@Entity
@Table(name = "FEEDBACKS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedbacks {

    /** 워커가 LLM 응답을 이 개수로 잘라 넣는다. 여기 예외는 그 절삭을 빠뜨렸을 때의 방어선이다. */
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

    /** 재현·과금 추적용으로 사용한 LLM 모델 ID 를 남긴다. */
    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<CorrectionSegments> correctionSegments = new ArrayList<>();

    /** 컬렉션이 둘이라 fetch join 을 겹치면 MultipleBagFetchException 이 난다. 이쪽은 배치 로딩한다. */
    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<FeedbackTips> tips = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ========= 생성 메서드 =========
    public static Feedbacks create(Letters letter, String correctedContent, String model) {
        Feedbacks feedback = new Feedbacks(letter, correctedContent, model);
        letter.registerFeedback(feedback);
        return feedback;
    }

    // ========= 연관관계 메서드 =========
    public void addCorrectionSegment(CorrectionSegments segment) {
        this.correctionSegments.add(segment);
    }

    public void addTip(FeedbackTips tip) {
        validateTipCount();
        this.tips.add(tip);
    }

    // ========= 비즈니스 로직 메서드 =========
    public boolean hasTips() {
        return !this.tips.isEmpty();
    }

    // ========= 생성자 =========
    private Feedbacks(Letters letter, String correctedContent, String model) {
        validateCorrectedContent(correctedContent);
        validateModel(model);
        this.letter = letter;
        this.correctedContent = correctedContent;
        this.model = model;
    }

    // ========= 검증 메서드 =========
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
