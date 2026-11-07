package com.redislab.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.redislab.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ClientIpResolverTest {

    @Mock
    private AppProperties appProperties;

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver(appProperties);
    }

    @Test
    void usesRemoteAddrByDefault() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void usesFirstForwardedForWhenTrustEnabled() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void replacesColonsInIpv6() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(false));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");

        assertThat(resolver.resolve(request)).isEqualTo("2001_db8__1");
    }

    private static AppProperties.RateLimit rateLimit(boolean trustForwarded) {
        return new AppProperties.RateLimit(true, 10, 1, Duration.ofSeconds(60), trustForwarded);
    }
}
