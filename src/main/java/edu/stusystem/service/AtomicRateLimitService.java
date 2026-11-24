package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class AtomicRateLimitService {
    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
                    "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
                    "if count >= tonumber(ARGV[2]) and redis.call('EXISTS', KEYS[2]) == 0 then " +
                    "redis.call('PSETEX', KEYS[2], ARGV[3], '1'); end; " +
                    "return count", Long.class);

    private final StringRedisTemplate redis;

    public AtomicRateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLimited(String lockKey) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(lockKey));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    public void recordFailure(String failureKey, String lockKey, long limit, Duration window, Duration lockDuration) {
        try {
            redis.execute(RECORD_FAILURE, List.of(failureKey, lockKey), Long.toString(window.toMillis()), Long.toString(limit), Long.toString(lockDuration.toMillis()));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    public void clear(String... keys) {
        try {
            redis.delete(List.of(keys));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    public static String opaqueKey(String normalizedIdentifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedIdentifier.getBytes(StandardCharsets.UTF_8));
            return "auth:login:unknown:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "登录保护暂不可用，请稍后重试");
    }
}
