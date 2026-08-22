package com.dearjolly.server.global.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dearjolly.seed.stamp")
public record StampSeedProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath*:seed/stamps/*.png") String location,
        @DefaultValue("stamp/") String keyPrefix
) {
}
