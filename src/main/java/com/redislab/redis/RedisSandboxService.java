package com.redislab.redis;

import com.redislab.cache.key.RedisKeyBuilder;
import com.redislab.common.exception.RedisSandboxKeyNotFoundException;
import com.redislab.common.exception.RedisUnavailableException;
import com.redislab.redis.dto.CreateSandboxKeyRequest;
import com.redislab.redis.dto.CreateSandboxKeyResponse;
import com.redislab.redis.dto.DeleteSandboxKeyResponse;
import com.redislab.redis.dto.ExistsSandboxKeyResponse;
import com.redislab.redis.dto.ExpireSandboxKeyResponse;
import com.redislab.redis.dto.GetSandboxKeyResponse;
import com.redislab.redis.dto.TtlSandboxKeyResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class RedisSandboxService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;

    public RedisSandboxService(StringRedisTemplate redisTemplate, RedisKeyBuilder keyBuilder) {
        this.redisTemplate = redisTemplate;
        this.keyBuilder = keyBuilder;
    }

    public CreateSandboxKeyResponse create(CreateSandboxKeyRequest request) {
        String storedAs = keyBuilder.sandbox(request.key());
        try {
            if (request.ttlSeconds() != null) {
                redisTemplate
                        .opsForValue()
                        .set(storedAs, request.value(), Duration.ofSeconds(request.ttlSeconds()));
            } else {
                redisTemplate.opsForValue().set(storedAs, request.value());
            }
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
        return new CreateSandboxKeyResponse(request.key(), storedAs, request.ttlSeconds());
    }

    public GetSandboxKeyResponse get(String logicalKey) {
        String storedAs = keyBuilder.sandbox(logicalKey);
        try {
            String value = redisTemplate.opsForValue().get(storedAs);
            if (value == null) {
                throw new RedisSandboxKeyNotFoundException(logicalKey);
            }
            return new GetSandboxKeyResponse(logicalKey, value);
        } catch (RedisSandboxKeyNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
    }

    public DeleteSandboxKeyResponse delete(String logicalKey) {
        String storedAs = keyBuilder.sandbox(logicalKey);
        try {
            Boolean deleted = redisTemplate.delete(storedAs);
            return new DeleteSandboxKeyResponse(logicalKey, Boolean.TRUE.equals(deleted));
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
    }

    public TtlSandboxKeyResponse ttl(String logicalKey) {
        String storedAs = keyBuilder.sandbox(logicalKey);
        try {
            Long ttl = redisTemplate.getExpire(storedAs, TimeUnit.SECONDS);
            if (ttl == null) {
                throw new RedisUnavailableException();
            }
            return new TtlSandboxKeyResponse(logicalKey, ttl);
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
    }

    public ExpireSandboxKeyResponse expire(String logicalKey, int seconds) {
        String storedAs = keyBuilder.sandbox(logicalKey);
        try {
            Boolean applied = redisTemplate.expire(storedAs, Duration.ofSeconds(seconds));
            return new ExpireSandboxKeyResponse(logicalKey, Boolean.TRUE.equals(applied));
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
    }

    public ExistsSandboxKeyResponse exists(String logicalKey) {
        String storedAs = keyBuilder.sandbox(logicalKey);
        try {
            Boolean exists = redisTemplate.hasKey(storedAs);
            return new ExistsSandboxKeyResponse(logicalKey, Boolean.TRUE.equals(exists));
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException();
        }
    }
}
