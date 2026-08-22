package com.dearjolly.server.global.seed;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dearjolly.seed.mock-user")
public record MockUserSeedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("KAKAO") OauthProvider oauthProvider,
        @DefaultValue("mock-user") String oauthId,
        @DefaultValue("mock@dearjolly.local") String email,
        @DefaultValue("jolly") String nickname
) {
}
