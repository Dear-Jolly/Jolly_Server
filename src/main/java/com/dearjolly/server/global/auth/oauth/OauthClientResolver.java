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

    /**
     * enum 값에 대응하는 클라이언트가 없다는 것은 사용자 입력 문제가 아니라 배선 누락이다.
     * 미지원 provider 문자열은 여기까지 오지 못하고 타입 변환 단계에서 COMMON_001 로 걸러진다.
     */
    public OauthClient resolve(OauthProvider provider) {
        OauthClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException("OauthClient 가 등록되지 않은 provider 다: " + provider);
        }
        return client;
    }
}
