package com.redislab.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.support.it.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminAndErrorFormatIT extends IntegrationTestBase {

    @Test
    void missingAdminKeyReturns401WithStandardBody() {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/admin/products",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("timestamp");
        assertThat(response.getBody()).contains("INVALID_ADMIN_KEY");
        assertThat(response.getBody()).contains("path");
        assertThat(response.getBody()).doesNotContain("Exception");
    }

    @Test
    void wrongAdminKeyReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", "wrong");
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/admin/products",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validationErrorHasFieldErrors() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/api/products?page=0&size=51", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_PAGINATION");
        assertThat(response.getBody()).contains("\"status\":400");
    }
}
