package com.dearjolly.server.domain.user.dto.response;

public record TermsAgreeResponse(
        boolean termsAgreed
) {
    public static TermsAgreeResponse from(boolean termsAgreed) {
        return new TermsAgreeResponse(termsAgreed);
    }
}
