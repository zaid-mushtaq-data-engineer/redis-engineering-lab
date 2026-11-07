package com.redislab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redislab.common.exception.RateLimitExceededException;
import com.redislab.common.util.ClientIpResolver;
import com.redislab.config.AppProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private TokenBucketRateLimiter rateLimiter;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(appProperties, clientIpResolver, rateLimiter);
    }

    @Test
    void passesThroughWhenDisabled() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(false));
        MockHttpServletRequest request = getRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        verify(rateLimiter, never()).tryConsume(anyString());
    }

    @Test
    void setsHeadersWhenAllowed() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(true));
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
        when(rateLimiter.tryConsume("127.0.0.1"))
                .thenReturn(Optional.of(new RateLimitResult(true, 5, 0, 1_700_000_000L, 10)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(getRequest(), response, new Object());

        assertThat(response.getHeader(RateLimitHeaders.LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(RateLimitHeaders.REMAINING)).isEqualTo("5");
        assertThat(response.getHeader(RateLimitHeaders.RESET)).isEqualTo("1700000000");
    }

    @Test
    void throwsWhenRejected() {
        RateLimitResult rejected = new RateLimitResult(false, 0, 3, 1_700_000_000L, 10);
        when(appProperties.rateLimit()).thenReturn(rateLimit(true));
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
        when(rateLimiter.tryConsume("127.0.0.1")).thenReturn(Optional.of(rejected));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> interceptor.preHandle(getRequest(), response, new Object()))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void noHeadersWhenRedisUnavailable() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(true));
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
        when(rateLimiter.tryConsume("127.0.0.1")).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(getRequest(), response, new Object());

        assertThat(response.getHeader(RateLimitHeaders.LIMIT)).isNull();
    }

    @Test
    void skipsNonGetRequests() {
        when(appProperties.rateLimit()).thenReturn(rateLimit(true));
        MockHttpServletRequest request = getRequest();
        request.setMethod("POST");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, never()).tryConsume(anyString());
    }

    private static MockHttpServletRequest getRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/products");
        return request;
    }

    private static AppProperties.RateLimit rateLimit(boolean enabled) {
        return new AppProperties.RateLimit(enabled, 10, 1, Duration.ofSeconds(60), false);
    }
}
