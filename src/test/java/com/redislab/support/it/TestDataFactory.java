package com.redislab.support.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redislab.product.dto.CreateProductRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
public class TestDataFactory {

    private final TestRestTemplate rest;
    private final ObjectMapper objectMapper;

    public TestDataFactory(TestRestTemplate rest, ObjectMapper objectMapper) {
        this.rest = rest;
        this.objectMapper = objectMapper;
    }

    public long createActiveProductWithStock(
            int port, HttpHeaders adminHeaders, String nameSuffix, int stock) {
        CreateProductRequest create =
                new CreateProductRequest("Widget-" + nameSuffix, "integration", new BigDecimal("19.99"));
        ResponseEntity<String> created = rest.exchange(
                "http://localhost:" + port + "/api/admin/products",
                HttpMethod.POST,
                new HttpEntity<>(create, adminHeaders),
                String.class);
        long productId = parseId(created.getBody());
        if (stock > 0) {
            rest.exchange(
                    "http://localhost:" + port + "/api/admin/inventory/" + productId,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("stockQuantity", stock, "version", 0), adminHeaders),
                    String.class);
        }
        return productId;
    }

    public long parseId(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.get("id").asLong();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse product id from: " + json, ex);
        }
    }

    public long parseVersion(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.get("version").asLong();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse version from: " + json, ex);
        }
    }

    public static HttpHeaders adminHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
