package com.dearjolly.server.domain.feedback.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockLlmClient implements LlmClient {
    private static final String MODEL = "mock-v1";
    private static final int MAX_TIP_COUNT = 3;

    private static final Map<String, String> CORRECTIONS = new LinkedHashMap<>();

    static {
        CORRECTIONS.put("a ordinary", "an ordinary");
        CORRECTIONS.put("to look", "looking");
        CORRECTIONS.put("make", "made");
        CORRECTIONS.put("cheer", "cheering");
        CORRECTIONS.put("which", "that");
        CORRECTIONS.put("got", "received");
    }

    private static final List<String> TIPS = List.of(
            "문장에 동사에 따라 to 부정사와 동명사가 오는 경우가 달라요! 그 부분을 확인해보세요!",
            "'that'은 선행사를 한정하는 필수 정보를, 'which'는 추가 정보를 제공하는 데 쓰여요! 또 'that'은 사람/사물 모두 가능하지만 'which'는 보통 사물에 쓰인답니다!",
            "이 문맥에서는 'got'보다 'received'가 더 자연스러워요."
    );

    @Override
    public LlmFeedback correct(String content, List<String> stampNames) {
        Correction corrected = applyCorrections(content);
        return new LlmFeedback(
                corrected.content(),
                TIPS.subList(0, Math.min(corrected.count(), MAX_TIP_COUNT)),
                pickStampName(content, stampNames),
                MODEL
        );
    }

    private Correction applyCorrections(String content) {
        StringBuilder corrected = new StringBuilder();
        int index = 0;
        int count = 0;
        while (index < content.length()) {
            String matched = matchAt(content, index);
            if (matched == null) {
                corrected.append(content.charAt(index));
                index++;
                continue;
            }
            corrected.append(CORRECTIONS.get(matched));
            index += matched.length();
            count++;
        }
        return new Correction(corrected.toString(), count);
    }

    private String matchAt(String content, int index) {
        if (index > 0 && isWordCharacter(content.charAt(index - 1))) {
            return null;
        }
        return CORRECTIONS.keySet().stream()
                .filter(target -> content.startsWith(target, index))
                .filter(target -> isWordBoundaryAfter(content, index + target.length()))
                .findFirst()
                .orElse(null);
    }

    private boolean isWordBoundaryAfter(String content, int end) {
        return end >= content.length() || !isWordCharacter(content.charAt(end));
    }

    private boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '\'';
    }

    // 실제 구현에서는 LLM 이 편지 분위기를 보고 고른다. mock 은 후보 중 하나를 편지 내용으로 고정 선택한다.
    private String pickStampName(String content, List<String> stampNames) {
        if (stampNames.isEmpty()) {
            return null;
        }
        List<String> candidates = new ArrayList<>(stampNames);
        return candidates.get(Math.floorMod(content.hashCode(), candidates.size()));
    }

    private record Correction(String content, int count) {
    }
}
