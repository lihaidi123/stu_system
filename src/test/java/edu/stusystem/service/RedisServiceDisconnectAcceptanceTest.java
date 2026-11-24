package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "REDIS_DISCONNECT_ACCEPTANCE", matches = "true")
class RedisServiceDisconnectAcceptanceTest {
    @Test
    void liveRedisProcessDisconnectFailsClosed() throws Exception {
        String host = System.getenv("REDIS_TEST_HOST");
        int port = Integer.parseInt(System.getenv("REDIS_TEST_PORT"));
        long pid = Long.parseLong(System.getenv("REDIS_TEST_SERVER_PID"));
        var redisProcess = ProcessHandle.of(pid).orElseThrow();
        var config = new RedisStandaloneConfiguration(host, port);
        var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build();
        var factory = new LettuceConnectionFactory(config, client);
        factory.afterPropertiesSet();
        factory.start();
        try {
            var template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            var limiter = new AtomicRateLimitService(template);
            assertFalse(limiter.isLimited("stage4:test:outage:acceptance"));
            assertTrue(redisProcess.destroyForcibly());
            assertFalse(redisProcess.onExit().get(10, TimeUnit.SECONDS).isAlive());

            ApiException failure = assertThrows(ApiException.class,
                    () -> limiter.isLimited("stage4:test:outage:acceptance"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
            assertEquals("RATE_LIMIT_UNAVAILABLE", failure.code());
        } finally {
            factory.destroy();
        }
    }
}
