package com.dearjolly.server.domain.letter.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Schema(description = "편지 목록 정렬 기준. 기준은 편지 날짜이며, 같은 날짜면 LATEST 는 나중에 쓴 편지가 먼저 온다")
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
