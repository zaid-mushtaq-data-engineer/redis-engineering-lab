package com.redislab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.ratelimit.RateLimitHeaders;
import com.redislab.ratelimit.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerRateLimitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitResponseIncludesHeadersAndBody() {
        RateLimitResult result = new RateLimitResult(false, 0, 4, 1_700_000_100L, 10);
        RateLimitExceededException ex = new RateLimitExceededException(result);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/products");

        ResponseEntity<?> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(RateLimitHeaders.RETRY_AFTER)).isEqualTo("4");
        assertThat(response.getHeaders().getFirst(RateLimitHeaders.LIMIT)).isEqualTo("10");
        assertThat(response.getHeaders().getFirst(RateLimitHeaders.REMAINING)).isEqualTo("0");
        assertThat(response.getHeaders().getFirst(RateLimitHeaders.RESET)).isEqualTo("1700000100");
        assertThat(response.getBody()).isNotNull();
    }
}
