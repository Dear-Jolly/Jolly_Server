package com.dearjolly.server.global.config;

import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtAuthenticationFilter;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/reissue",
            "/api/v1/admin/login",
            "/api/v1/version",
            "/actuator/health",
            "/error",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private static final List<String> JWT_FILTER_SKIP_PATHS = List.of(
            "/api/v1/auth/reissue",
            "/api/v1/auth/*/callback",
            "/api/v1/admin/login",
            "/api/v1/version",
            "/actuator/**",
            "/error",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AuthenticatedUserHolder authenticatedUserHolder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 로그인은 위 공개 경로에서 이미 걸러진다. 나머지 관리자 경로는 전부 권한을 본다.
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/*/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/*/callback").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(
                                jwtProvider, userRepository, authenticatedUserHolder,
                                objectMapper, JWT_FILTER_SKIP_PATHS),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, e) -> writeError(res, ErrorCode.ACCESS_TOKEN_INVALID))
                        .accessDeniedHandler((req, res, e) -> writeError(res, ErrorCode.ACCESS_DENIED))
                )
                .build();
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.toResponseEntity(errorCode).getBody()
        ));
    }
}
