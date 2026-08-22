package com.dearjolly.server.global.seed;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dearjolly.seed.user")
public record UserSeedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("KAKAO") OauthProvider oauthProvider,
        @DefaultValue("seed-admin") String oauthId,
        @DefaultValue("admin@dearjolly.local") String email,
        @DefaultValue("jolly") String nickname
) {
}
