package com.redislab.support.it;

import com.redislab.ItTestcontainersConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItTestcontainersConfig.class, IntegrationTestConfiguration.class})
public abstract class IntegrationTestBase {

    protected static final String ADMIN_KEY = "test-admin-key-for-integration-tests";

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected TestDataFactory testData;

    @Autowired
    protected RedisTestSupport redisTestSupport;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", ADMIN_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected <T> HttpEntity<T> adminJson(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }

    protected HttpEntity<Void> adminGet() {
        return new HttpEntity<>(adminHeaders());
    }
}
