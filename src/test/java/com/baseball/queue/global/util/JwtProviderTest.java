package com.baseball.queue.global.util;

import com.baseball.queue.global.config.JwtProperties;
import com.baseball.queue.global.config.QueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String TEST_SECRET =
            "dGVzdC1qd3Qtc2lnbmluZy1rZXktZm9yLWxvY2FsLXRlc3RzLTAxMjM0NTY=";

    @Test
    @DisplayName("동일한 외부 JWT 설정 키를 사용하는 인스턴스는 서로의 토큰을 검증한다")
    void providersWithSameConfiguredKeyValidateEachOthersTokens() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        QueueProperties queueProperties = new QueueProperties();

        JwtProvider issuingProvider = new JwtProvider(properties, queueProperties);
        JwtProvider validatingProvider = new JwtProvider(properties, queueProperties);
        issuingProvider.initializeKey();
        validatingProvider.initializeKey();

        String token = issuingProvider.generateActiveToken("active_user");

        assert "active_user".equals(validatingProvider.validate(token).userId());
        assert "ACTIVE_USER".equals(validatingProvider.validate(token).role());
    }
}
