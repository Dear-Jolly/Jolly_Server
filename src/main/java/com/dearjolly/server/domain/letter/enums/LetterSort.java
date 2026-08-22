package com.dearjolly.server.domain.letter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Getter
@RequiredArgsConstructor
public enum LetterSort {
    LATEST("최신순", Sort.Direction.DESC),
    OLDEST("오래된 순", Sort.Direction.ASC);

    private final String description;
    private final Sort.Direction direction;

    public Sort toSort() {
        return Sort.by(direction, "letterDate").and(Sort.by(direction, "id"));
    }
}
