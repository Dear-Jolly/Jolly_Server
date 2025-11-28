package com.dearjolly.server.entity;

import com.dearjolly.server.entity.enums.Status;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Setter;

@Entity
@Table(name = "LETTERS")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Letters {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "letter_id")
    private Long id;

    @Setter
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

    public static Letters create(Users user, String content, LocalDate letterDate, Status status) {
        Letters letter = new Letters(user, content, letterDate, status);
        user.addLetter(letter);
        return letter;
    }

    public void setFeedback(Feedbacks feedback) {
        this.feedback = feedback;
        if (feedback.getLetter() != this) {
            feedback.setLetter(this);
        }
    }
}
