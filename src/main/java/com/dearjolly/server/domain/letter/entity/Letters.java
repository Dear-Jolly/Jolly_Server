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
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "letter_id")
    private Long id;

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

    public static Letters create(Users user, String content, LocalDate letterDate, ZoneId timeZone) {
        Letters letter = new Letters(user, content, letterDate, timeZone);
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
    }

    public void completeFeedback(Stamps stamp) {
        validateStamp(stamp);
        this.stamp = stamp;
        this.status = FEEDBACK_COMPLETED;
    }

    public void retryFeedback() {
        this.status = SUBMITTED;
        this.retryCount++;
    }

    public void requeueFeedback() {
        this.status = SUBMITTED;
    }

    public void failFeedback() {
        this.status = FEEDBACK_FAILED;
    }

    public void resetForReprocess() {
        this.status = SUBMITTED;
        this.retryCount = 0;
    }

    public boolean isRetryExhausted() {
        return this.retryCount >= MAX_RETRY_COUNT;
    }

    public boolean isFeedbackCompleted() {
        return this.status.isCompleted();
    }

    public LocalDateTime createdAtInWrittenZone() {
        return this.createdAt.atZone(SERVER_ZONE)
                .withZoneSameInstant(ZoneId.of(this.timeZone))
                .toLocalDateTime();
    }

    public Status toResponseStatus() {
        return this.status == FEEDBACK_FAILED ? SUBMITTED : this.status;
    }

    private Letters(Users user, String content, LocalDate letterDate, ZoneId timeZone) {
        validateContent(content);
        validateLetterDate(letterDate);
        validateTimeZone(timeZone);
        this.user = user;
        this.content = content;
        this.letterDate = letterDate;
        this.timeZone = timeZone.getId();
        this.status = SUBMITTED;
        this.isRead = false;
        this.retryCount = 0;
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
