package com.redislab.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.redislab.ItTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItTestcontainersConfig.class)
@Isolated
class ActuatorHealthIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private GenericContainer<?> redisContainer;

    @Test
    void healthUpWhenRedisUp() {
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"redis\"");
    }

    @Test
    void healthStaysUpWhenRedisStopped() {
        redisContainer.stop();
        try {
            ResponseEntity<String> response =
                    rest.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"status\":\"UP\"");
            assertThat(response.getBody()).contains("DEGRADED");
        } finally {
            redisContainer.start();
        }
    }
}
