package com.dearjolly.server.domain.letter.enums;

public enum Status {
    DRAFT("작성중"),
    SUBMITTED("제출됨"),

    ANALYZED("피드백완료");

    final String description;

    Status(String description) {
        this.description = description;
    }
}
