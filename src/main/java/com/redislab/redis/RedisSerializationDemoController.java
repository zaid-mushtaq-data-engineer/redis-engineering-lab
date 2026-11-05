package com.redislab.redis;

import com.redislab.redis.dto.JdkSerializationDemoResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/redis/serialization")
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-endpoints.enabled", havingValue = "true")
public class RedisSerializationDemoController {

    private final RedisSerializationDemoService demoService;

    public RedisSerializationDemoController(RedisSerializationDemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/jdk-demo")
    @ResponseStatus(HttpStatus.OK)
    public JdkSerializationDemoResponse jdkDemo() {
        return demoService.runDemo();
    }
}
