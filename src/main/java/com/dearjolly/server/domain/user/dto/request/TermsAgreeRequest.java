package com.dearjolly.server.domain.user.dto.request;

import com.dearjolly.server.domain.user.enums.TermsType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TermsAgreeRequest(

        @NotEmpty(message = "약관 동의 목록은 비어 있을 수 없습니다.")
        @Valid
        List<Agreement> agreements
) {
    public record Agreement(

            @NotNull(message = "약관 종류는 필수입니다.")
            TermsType type,

            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean agreed
    ) {
    }
}
