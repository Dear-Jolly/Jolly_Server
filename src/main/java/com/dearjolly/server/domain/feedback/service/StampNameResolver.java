package com.dearjolly.server.domain.feedback.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StampNameResolver {
    public String resolve(String stampName, List<String> candidates) {
        if (stampName == null || stampName.isBlank()) {
            throw new IllegalStateException("OpenAI가 우표를 선택하지 않았습니다.");
        }
        return candidates.stream()
                .filter(candidate -> candidate.equals(stampName))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(candidate -> normalize(candidate).equals(normalize(stampName)))
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI가 후보에 없는 우표를 선택했습니다: " + stampName));
    }

    // 이름의 구분자는 사람이 읽으라고 넣은 것이라 의미가 없다. 밑줄까지 정확히 맞히기를 요구하면
    // "꽃_장미" 대신 "꽃 장미" 를 돌려준 것만으로 교정문과 팁까지 통째로 버리게 된다.
    // 부분 일치는 허용하지 않는다. "친구" 와 "친구_위로" 처럼 한쪽이 다른 쪽에 포함되는 후보가 있다.
    private String normalize(String name) {
        return name.replaceAll("[\\s_·]", "");
    }
}
