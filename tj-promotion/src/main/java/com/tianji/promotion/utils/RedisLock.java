package com.tianji.promotion.utils;

import com.mysql.cj.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisLock {

    private final String key;
    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(long timeout, TimeUnit timeUnit) {
        //获取线程名
        String value = Thread.currentThread().getName();
        // 尝试获取锁
        Boolean b = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
        return b != null && b;
    }

    public void unlock() {
        redisTemplate.delete(key);
    }
}
