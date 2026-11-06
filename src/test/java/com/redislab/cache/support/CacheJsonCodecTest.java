package com.redislab.cache.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislab.common.response.PageResponse;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheJsonCodecTest {

    private final CacheJsonCodec codec = new CacheJsonCodec(new ObjectMapper());

    @Test
    void roundTripsProductResponse() throws Exception {
        ProductResponse original =
                new ProductResponse(7L, "Mouse", "Wireless", new BigDecimal("19.50"), ProductStatus.ACTIVE);

        String json = codec.toJson(original);
        ProductResponse parsed = codec.fromJson(json, ProductResponse.class).orElseThrow();

        assertThat(json).doesNotContain("@class");
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void invalidJsonIsEmpty() {
        assertThat(codec.fromJson("{not-json", ProductResponse.class)).isEmpty();
    }

    @Test
    void roundTripsProductListPage() throws Exception {
        PageResponse<ProductResponse> original = new PageResponse<>(
                List.of(new ProductResponse(1L, "A", "d", new BigDecimal("2.00"), ProductStatus.ACTIVE)),
                0,
                20,
                1,
                1,
                true);
        TypeReference<PageResponse<ProductResponse>> type = new TypeReference<>() {};

        String json = codec.toJson(original);
        PageResponse<ProductResponse> parsed = codec.fromJson(json, type).orElseThrow();

        assertThat(json).doesNotContain("@class");
        assertThat(parsed).isEqualTo(original);
    }
}
