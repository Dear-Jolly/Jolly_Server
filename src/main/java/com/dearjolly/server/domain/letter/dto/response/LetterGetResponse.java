package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.feedback.dto.response.FeedbackGetResponse;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import java.time.LocalDate;

public record LetterGetResponse(
        Long letterId,
        LocalDate date,
        String originalContent,
        Status status,
        String stampImage,
        FeedbackGetResponse feedback
) {
    public static LetterGetResponse of(Letters letter, String stampImage) {
        return new LetterGetResponse(
                letter.getId(),
                letter.getLetterDate(),
                letter.getContent(),
                letter.toResponseStatus(),
                stampImage,
                feedbackOf(letter)
        );
    }

    private static FeedbackGetResponse feedbackOf(Letters letter) {
        Feedbacks feedback = letter.getFeedback();
        if (!letter.isFeedbackCompleted() || feedback == null) {
            return null;
        }
        return FeedbackGetResponse.from(feedback);
    }
}
