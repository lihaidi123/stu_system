package edu.stusystem.service;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisLockProbe {
    private RedisLockProbe() {}

    public static void main(String[] args) {
        var host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        var port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        var factory=new LettuceConnectionFactory(host,port);
        factory.afterPropertiesSet();factory.start();
        try {
            var redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
            System.exit(Boolean.TRUE.equals(redis.hasKey(args[0]))?0:1);
        } finally { factory.destroy(); }
    }
}
