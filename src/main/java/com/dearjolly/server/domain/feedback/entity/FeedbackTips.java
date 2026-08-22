package com.dearjolly.server.domain.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "FEEDBACK_TIPS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tips_order",
                columnNames = {"feedback_id", "sort_order"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackTips {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_tip_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedbacks feedback;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static FeedbackTips create(Feedbacks feedback, String content, int sortOrder) {
        FeedbackTips tip = new FeedbackTips(feedback, content, sortOrder);
        feedback.addTip(tip);
        return tip;
    }

    private FeedbackTips(Feedbacks feedback, String content, int sortOrder) {
        validateContent(content);
        validateSortOrder(sortOrder);
        this.feedback = feedback;
        this.content = content;
        this.sortOrder = sortOrder;
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("학습 팁 내용은 필수입니다.");
        }
    }

    private void validateSortOrder(int sortOrder) {
        if (sortOrder < 1) {
            throw new IllegalArgumentException("표시 순서는 1부터 시작합니다.");
        }
    }
}
