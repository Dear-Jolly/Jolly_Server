package com.dearjolly.server.global.auth.oauth;

import com.dearjolly.server.domain.user.enums.OauthProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OauthClientResolver {
    private final Map<OauthProvider, OauthClient> clients = new EnumMap<>(OauthProvider.class);

    public OauthClientResolver(List<OauthClient> oauthClients) {
        oauthClients.forEach(client -> clients.put(client.supports(), client));
    }

    public OauthClient resolve(OauthProvider provider) {
        OauthClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException("OauthClient 가 등록되지 않은 provider 다: " + provider);
        }
        return client;
    }
}
