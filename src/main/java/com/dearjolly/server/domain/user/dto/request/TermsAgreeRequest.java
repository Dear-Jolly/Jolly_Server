package com.dearjolly.server.domain.user.dto.request;

import com.dearjolly.server.domain.user.enums.TermsType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "약관 동의 요청. 보내지 않은 항목은 그대로 유지된다")
public record TermsAgreeRequest(

        @Schema(description = "약관 동의 목록 (1개 이상)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "약관 동의 목록은 비어 있을 수 없습니다.")
        @Valid
        List<Agreement> agreements
) {
    @Schema(description = "약관 한 건의 동의 여부")
    public record Agreement(

            @Schema(description = "약관 종류. SERVICE · PRIVACY 는 필수, MARKETING 은 선택",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "약관 종류는 필수입니다.")
            TermsType type,

            @Schema(description = "동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean agreed
    ) {
    }
}
