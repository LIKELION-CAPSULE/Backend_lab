package com.capsule.common.ratelimit;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 요청 횟수 제한. 예: 인증번호 1분 1번, 메시지 1분 30번. */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * window 안에서 max번까지 true, 넘으면 false.
     * ponytail: 고정 창(INCR + 첫 요청에 EXPIRE). 창 경계에서 최대 2배까지 통과함. 더 엄격해야 하면 슬라이딩 창으로.
     */
    public boolean tryAcquire(String key, int max, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        return count != null && count <= max;
    }

    public void reset(String key) {
        redis.delete(key);
    }
}
