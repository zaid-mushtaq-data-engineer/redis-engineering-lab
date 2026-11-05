package com.redislab.redis;

import com.redislab.redis.dto.CreateSandboxKeyRequest;
import com.redislab.redis.dto.CreateSandboxKeyResponse;
import com.redislab.redis.dto.DeleteSandboxKeyResponse;
import com.redislab.redis.dto.ExistsSandboxKeyResponse;
import com.redislab.redis.dto.ExpireSandboxKeyRequest;
import com.redislab.redis.dto.ExpireSandboxKeyResponse;
import com.redislab.redis.dto.GetSandboxKeyResponse;
import com.redislab.redis.dto.TtlSandboxKeyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/redis/keys")
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-endpoints.enabled", havingValue = "true")
@Validated
public class RedisSandboxController {

    private static final String KEY_PATTERN = "[A-Za-z0-9:_-]{1,100}";

    private final RedisSandboxService sandboxService;

    public RedisSandboxController(RedisSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public CreateSandboxKeyResponse create(@Valid @RequestBody CreateSandboxKeyRequest request) {
        return sandboxService.create(request);
    }

    @GetMapping("/{key}")
    public GetSandboxKeyResponse get(
            @PathVariable @Pattern(regexp = KEY_PATTERN, message = "Invalid key format") String key) {
        return sandboxService.get(key);
    }

    @DeleteMapping("/{key}")
    public DeleteSandboxKeyResponse delete(
            @PathVariable @Pattern(regexp = KEY_PATTERN, message = "Invalid key format") String key) {
        return sandboxService.delete(key);
    }

    @GetMapping("/{key}/ttl")
    public TtlSandboxKeyResponse ttl(
            @PathVariable @Pattern(regexp = KEY_PATTERN, message = "Invalid key format") String key) {
        return sandboxService.ttl(key);
    }

    @PostMapping("/{key}/expire")
    public ExpireSandboxKeyResponse expire(
            @PathVariable @Pattern(regexp = KEY_PATTERN, message = "Invalid key format") String key,
            @Valid @RequestBody ExpireSandboxKeyRequest request) {
        return sandboxService.expire(key, request.seconds());
    }

    @GetMapping("/{key}/exists")
    public ExistsSandboxKeyResponse exists(
            @PathVariable @Pattern(regexp = KEY_PATTERN, message = "Invalid key format") String key) {
        return sandboxService.exists(key);
    }
}
