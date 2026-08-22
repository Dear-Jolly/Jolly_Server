package com.dearjolly.server.global.config;

import com.dearjolly.server.global.auth.interceptor.OnboardingInterceptor;
import com.dearjolly.server.global.auth.principal.LoginUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private static final String[] ONBOARDING_REQUIRED_PATHS = {
            "/api/v1/letters",
            "/api/v1/letters/**",
            "/api/v1/home"
    };

    private final OnboardingInterceptor onboardingInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingInterceptor)
                .addPathPatterns(ONBOARDING_REQUIRED_PATHS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
