package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.enums.TermsType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 동의 이력에서 현재 상태를 뽑아내는 규칙을 한 곳에 모은다.
 * 현재 동의 상태 = (user_id, type) 별 agreed_at 이 가장 최신인 행의 agreed 다 (ERD §2.2).
 */
final class TermsAgreementReader {

    private TermsAgreementReader() {
    }

    /** 이력은 agreed_at DESC 로 정렬돼 들어온다. type 별 첫 행이 최신이다. */
    static Map<TermsType, Boolean> toCurrentState(List<TermsAgreements> historyOrderedByAgreedAtDesc) {
        Map<TermsType, Boolean> current = new LinkedHashMap<>();
        for (TermsAgreements agreement : historyOrderedByAgreedAtDesc) {
            current.putIfAbsent(agreement.getType(), agreement.isAgreed());
        }
        return current;
    }

    static boolean isRequiredTermsAgreed(Map<TermsType, Boolean> currentState) {
        return Arrays.stream(TermsType.values())
                .filter(TermsType::isRequired)
                .allMatch(type -> Boolean.TRUE.equals(currentState.get(type)));
    }

    static boolean isMarketingAgreed(Map<TermsType, Boolean> currentState) {
        return Boolean.TRUE.equals(currentState.get(TermsType.MARKETING));
    }
}
