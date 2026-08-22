package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import org.springframework.core.convert.converter.Converter;

public class OauthProviderConverter implements Converter<String, OauthProvider> {
    @Override
    public OauthProvider convert(String source) {
        return OauthProvider.from(source);
    }
}
