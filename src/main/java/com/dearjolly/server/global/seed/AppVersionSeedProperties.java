package com.dearjolly.server.global.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dearjolly.seed.app-version")
public record AppVersionSeedProperties(
        @DefaultValue("true") boolean enabled
) {
}
