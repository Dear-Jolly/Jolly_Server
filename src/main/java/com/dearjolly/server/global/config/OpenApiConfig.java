package com.dearjolly.server.global.config;

import com.dearjolly.server.global.auth.principal.LoginUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_SCHEME = "bearerAuth";

    private static final String DESCRIPTION = """
            Dear Jolly 앱과 서버 사이의 계약 문서다.

            ### 공통 규약
            - 인증: `Authorization: Bearer {accessToken}` (액세스 30분 / 리프레시 14일)
            - Content-Type: `application/json; charset=UTF-8`
            - 날짜 `yyyy-MM-dd` · 날짜+시각 `yyyy-MM-dd'T'HH:mm:ss`
            - 성공 응답은 래퍼 없이 DTO 를 그대로 내려준다. 조회·수정 `200`, 생성 `201`, 본문 없음 `204`.
            - 실패 응답은 모든 API 가 `{ status, code, message }` 형태로 같다. `message` 는 유저에게 그대로 보여줘도 된다.

            ### 온보딩 가드
            필수 약관 2종 동의 + 닉네임 등록을 마쳐야 편지 · 홈 API 를 호출할 수 있다. 미완료면 `USER_005` 다.

            ### 모든 API 공통 에러
            | code | status | message |
            | --- | --- | --- |
            | `COMMON_001` | 400 | 잘못된 요청입니다. |
            | `COMMON_002` | 404 | 요청하신 경로를 찾을 수 없습니다. |
            | `COMMON_003` | 405 | 지원하지 않는 요청 방식입니다. |
            | `COMMON_004` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. |
            | `COMMON_005` | 500 | 일시적인 오류가 발생했습니다. |

            ### 인증이 필요한 API 공통 에러
            | code | status | message |
            | --- | --- | --- |
            | `AUTH_005` | 401 | 유효하지 않은 토큰입니다. |
            | `AUTH_006` | 403 | 접근 권한이 없습니다. |
            | `AUTH_007` | 401 | 탈퇴한 계정입니다. 다시 로그인해주세요. |
            """;

    static {
        // @LoginUser 는 토큰에서 유저를 꺼내는 서버 내부 파라미터다. 등록하지 않으면
        // springdoc 이 평범한 Long 파라미터로 보고 userId 를 쿼리 파라미터로 문서에 노출한다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dear Jolly API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public GroupedOpenApi allApi() {
        return group("all", "전체", "/api/v1/**");
    }

    @Bean
    public GroupedOpenApi authApi() {
        return group("auth", "인증", "/api/v1/auth/**");
    }

    @Bean
    public GroupedOpenApi userApi() {
        return group("user", "사용자", "/api/v1/users/**");
    }

    @Bean
    public GroupedOpenApi letterApi() {
        return group("letter", "편지 · 홈", "/api/v1/letters/**", "/api/v1/home");
    }

    @Bean
    public GroupedOpenApi versionApi() {
        return group("version", "버전", "/api/v1/version");
    }

    private GroupedOpenApi group(String name, String displayName, String... paths) {
        return GroupedOpenApi.builder()
                .group(name)
                .displayName(displayName)
                .pathsToMatch(paths)
                .build();
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("소셜 로그인 딥링크로 받은 accessToken 을 그대로 넣는다. `Bearer` 는 붙이지 않는다.");
    }
}
