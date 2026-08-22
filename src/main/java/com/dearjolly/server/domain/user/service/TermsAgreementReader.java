package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.enums.TermsType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TermsAgreementReader {
    private TermsAgreementReader() {
    }

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
