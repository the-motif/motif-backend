package com.motif.common.lock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 분산 락 / SET NX PX로 잡고, 내가 잡은 락만 Lua로 해제
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final String PREFIX = "lock:";

    // 값이 내 토큰일 때만 삭제 / lease 만료 후 남이 잡은 락 삭제 방지
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    // 성공 시 토큰, 실패 시 null / 기다리지 않음
    public @Nullable String tryLock(String key, Duration lease) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, token, lease);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void unlock(String key, String token) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + key), token);
    }
}
