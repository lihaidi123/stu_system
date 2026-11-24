package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtomicRateLimitServiceRedisTest {
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private final String suffix = UUID.randomUUID().toString();
    private String failureKey;
    private String lockKey;

    @BeforeAll
    static void connectRedis() {
        factory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedis() {
        factory.destroy();
    }

    @AfterEach
    void removeTestKeys() {
        if (failureKey != null) redis.delete(failureKey);
        if (lockKey != null) redis.delete(lockKey);
    }

    @Test
    void fifthFailureStartsIndependentLockAfterTheFailureWindowExpires() throws Exception {
        failureKey = "stage4:test:login:" + suffix + ":failures";
        lockKey = "stage4:test:login:" + suffix + ":locked";
        AtomicRateLimitService limiter = new AtomicRateLimitService(redis);

        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.recordFailure(failureKey, lockKey, 5, Duration.ofMillis(700), Duration.ofMillis(1700));
        }
        assertFalse(limiter.isLimited(lockKey));
        limiter.recordFailure(failureKey, lockKey, 5, Duration.ofMillis(700), Duration.ofMillis(1700));
        assertTrue(limiter.isLimited(lockKey));

        Thread.sleep(850);
        assertNull(redis.opsForValue().get(failureKey));
        assertTrue(limiter.isLimited(lockKey));

        Thread.sleep(1000);
        assertFalse(limiter.isLimited(lockKey));
    }

    @Test
    void redisFailureIsReportedAsServiceUnavailable() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        ApiException error = assertThrows(ApiException.class,
                () -> new AtomicRateLimitService(unavailable).isLimited("stage4:test:unavailable"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        assertEquals("RATE_LIMIT_UNAVAILABLE", error.code());
    }

    @Test
    void refusedRedisConnectionFailsClosed() {
        LettuceConnectionFactory unavailableFactory = new LettuceConnectionFactory("127.0.0.1", 1);
        unavailableFactory.afterPropertiesSet();
        unavailableFactory.start();
        try {
            StringRedisTemplate unavailable = new StringRedisTemplate(unavailableFactory);
            unavailable.afterPropertiesSet();
            ApiException error = assertThrows(ApiException.class,
                    () -> new AtomicRateLimitService(unavailable).isLimited("stage4:test:redis-offline"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        } finally {
            unavailableFactory.destroy();
        }
    }

    @Test
    void unknownIdentifierKeysDoNotContainTheIdentifier() {
        String identifier = "private-test-identifier-" + suffix;

        String key = AtomicRateLimitService.opaqueKey(identifier);

        assertNotEquals(identifier, key);
        assertFalse(key.contains(identifier));
    }
}
