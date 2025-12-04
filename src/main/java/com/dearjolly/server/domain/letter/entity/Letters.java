package com.dearjolly.server.domain.letter.entity;

import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "LETTERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Letters {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "letter_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @OneToOne(mappedBy = "letter", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Feedbacks feedback;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "letter_date", nullable = false)
    private LocalDate letterDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private Letters(Users user, String content, LocalDate letterDate, Status status) {
        this.user = user;
        this.content = content;
        this.letterDate = letterDate;
        this.status = status;
    }

    // ========= 생성 메서드 =========
    public static Letters create(Users user, String content, LocalDate letterDate, Status status) {
        Letters letter = new Letters(user, content, letterDate, status);
        user.addLetter(letter);
        return letter;
    }

    // ========= 연관관계 메서드 =========
    public void registerFeedback(Feedbacks feedback) {
        this.feedback = feedback;
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateContent(String newContent) {
        this.content = newContent;
    }

    public void updateStatus(Status newStatus) {
        this.status = newStatus;
    }
}
