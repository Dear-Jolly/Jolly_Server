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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "LETTERS",
        indexes = {
                @Index(name = "idx_letters_list", columnList = "user_id, letter_date DESC, letter_id DESC"),
                @Index(name = "idx_letters_retry", columnList = "status, next_retry_at"),
                @Index(name = "idx_letters_stalled", columnList = "status, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Letters {
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "letter_id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stamp_id")
    private Stamps stamp;

    @OneToOne(mappedBy = "letter", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Feedbacks feedback;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "letter_date", nullable = false)
    private LocalDate letterDate;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

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

    public static Letters create(
            Users user, String content, LocalDate letterDate, ZoneId timeZone, Stamps defaultStamp
    ) {
        Letters letter = new Letters(user, content, letterDate, timeZone, defaultStamp);
        user.addLetter(letter);
        return letter;
    }

    public void registerFeedback(Feedbacks feedback) {
        this.feedback = feedback;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void startFeedback() {
        this.status = FEEDBACK_IN_PROGRESS;
        this.nextRetryAt = null;
    }

    public void completeFeedback(Stamps stamp) {
        validateStamp(stamp);
        this.stamp = stamp;
        this.status = FEEDBACK_COMPLETED;
        this.nextRetryAt = null;
    }

    // 첫 실패부터 실패 우표를 붙인다. 재시도가 성공하면 completeFeedback 이 LLM 우표로 덮어쓴다.
    // 우표 시드가 돌지 않은 환경에서는 우표를 그대로 두고 재시도 예약만 남긴다.
    public void scheduleRetry(LocalDateTime nextRetryAt, Stamps failedStamp) {
        if (nextRetryAt == null) {
            throw new IllegalArgumentException("다음 재시도 시각은 필수입니다.");
        }
        if (failedStamp != null) {
            this.stamp = failedStamp;
        }
        this.status = SUBMITTED;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
    }

    public void failFeedback() {
        this.status = FEEDBACK_FAILED;
        this.nextRetryAt = null;
    }

    public void resetForReprocess(Stamps defaultStamp) {
        this.stamp = defaultStamp;
        this.status = SUBMITTED;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    public boolean isFeedbackCompleted() {
        return this.status.isCompleted();
    }

    public LocalDateTime createdAtInWrittenZone() {
        return this.createdAt.atZone(SERVER_ZONE)
                .withZoneSameInstant(ZoneId.of(this.timeZone))
                .toLocalDateTime();
    }

    // 한 번이라도 실패했으면 내부적으로 재시도 중이어도 앱에는 실패로만 보인다.
    // 재시도 성공 여부에 따라 준비 중 ↔ 실패를 오가면 앱이 안내 문구를 번복하게 된다.
    public Status toResponseStatus() {
        if (this.retryCount > 0 && this.status != FEEDBACK_COMPLETED) {
            return FEEDBACK_FAILED;
        }
        return this.status;
    }

    private Letters(Users user, String content, LocalDate letterDate, ZoneId timeZone, Stamps defaultStamp) {
        validateContent(content);
        validateLetterDate(letterDate);
        validateTimeZone(timeZone);
        this.user = user;
        this.content = content;
        this.letterDate = letterDate;
        this.timeZone = timeZone.getId();
        // 피드백 완료 전까지는 "준비 중" 우표가 붙어 있다가 completeFeedback 에서 교체된다.
        this.stamp = defaultStamp;
        this.status = SUBMITTED;
        this.isRead = false;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

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

    private void validateTimeZone(ZoneId timeZone) {
        if (timeZone == null) {
            throw new IllegalArgumentException("작성 타임존은 필수입니다.");
        }
    }

    private void validateStamp(Stamps stamp) {
        if (stamp == null) {
            throw new IllegalArgumentException("피드백 완료 시 우표는 필수입니다.");
        }
    }
}
