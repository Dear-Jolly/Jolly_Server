package com.dearjolly.server.domain.letter.entity;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_COMPLETED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_FAILED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;

import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.user.entity.Users;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편지는 append-only 다. 등록 후 content 와 letterDate 는 변경되지 않으며
 * 사용자 요청으로 삭제되지도 않는다. 그래서 본문 수정 메서드를 두지 않는다.
 */
@Entity
@Table(
        name = "LETTERS",
        indexes = {
                @Index(name = "idx_letters_list", columnList = "user_id, letter_date DESC, letter_id DESC"),
                @Index(name = "idx_letters_pending", columnList = "status, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Letters {

    private static final int MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "letter_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    /** 마스터 데이터라 단방향이다. cascade 를 걸지 않으므로 편지 삭제가 우표 행에 영향을 주지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stamp_id")
    private Stamps stamp;

    @OneToOne(mappedBy = "letter", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Feedbacks feedback;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "letter_date", nullable = false)
    private LocalDate letterDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========= 생성 메서드 =========

    /**
     * 임시저장이 없으므로 편지는 항상 SUBMITTED 로 생성된다.
     * letterDate 는 요청의 writtenAt 을 요청 타임존 기준으로 환산한 날짜다.
     */
    public static Letters create(Users user, String content, LocalDate letterDate) {
        Letters letter = new Letters(user, content, letterDate);
        user.addLetter(letter);
        return letter;
    }

    // ========= 연관관계 메서드 =========
    public void registerFeedback(Feedbacks feedback) {
        this.feedback = feedback;
    }

    // ========= 비즈니스 로직 메서드 =========

    /** 상세 조회의 부수 효과. 한 번 읽으면 다시 미열람으로 되돌아가지 않는다. */
    public void markAsRead() {
        this.isRead = true;
    }

    /**
     * 워커 픽업. 실제 중복 실행 방지는 레포지터리의 조건부 UPDATE 가 담당하며,
     * 이 메서드는 영속성 컨텍스트의 상태를 맞추기 위한 것이다.
     */
    public void startFeedback() {
        this.status = FEEDBACK_IN_PROGRESS;
    }

    /** 우표 부여와 완료 전이는 항상 함께 일어난다 (불변식 A9). */
    public void completeFeedback(Stamps stamp) {
        validateStamp(stamp);
        this.stamp = stamp;
        this.status = FEEDBACK_COMPLETED;
    }

    /** LLM 실패 후 재시도. 워커가 백오프 뒤 자신을 재예약한다. */
    public void retryFeedback() {
        this.status = SUBMITTED;
        this.retryCount++;
    }

    /** 처리 유실(15분 초과) 감지 시 되돌린다. 재시도 횟수는 늘리지 않는다. */
    public void requeueFeedback() {
        this.status = SUBMITTED;
    }

    public void failFeedback() {
        this.status = FEEDBACK_FAILED;
    }

    /** 운영자 수동 재처리. 재시도 횟수를 초기화한다. */
    public void resetForReprocess() {
        this.status = SUBMITTED;
        this.retryCount = 0;
    }

    public boolean isRetryExhausted() {
        return this.retryCount > MAX_RETRY_COUNT;
    }

    public boolean isFeedbackCompleted() {
        return this.status.isCompleted();
    }

    /** FEEDBACK_FAILED 는 내부 상태이므로 응답에서는 SUBMITTED 로 치환한다. */
    public Status toResponseStatus() {
        return this.status == FEEDBACK_FAILED ? SUBMITTED : this.status;
    }

    // ========= 생성자 =========
    private Letters(Users user, String content, LocalDate letterDate) {
        validateContent(content);
        validateLetterDate(letterDate);
        this.user = user;
        this.content = content;
        this.letterDate = letterDate;
        this.status = SUBMITTED;
        this.isRead = false;
        this.retryCount = 0;
    }

    // ========= 검증 메서드 =========
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("편지 내용은 필수입니다.");
        }
    }

    private void validateLetterDate(LocalDate letterDate) {
        if (letterDate == null) {
            throw new IllegalArgumentException("편지 날짜는 필수입니다.");
        }
    }

    private void validateStamp(Stamps stamp) {
        if (stamp == null) {
            throw new IllegalArgumentException("피드백 완료 시 우표는 필수입니다.");
        }
    }
}
