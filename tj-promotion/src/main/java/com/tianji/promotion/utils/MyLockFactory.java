package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.promotion.enums.MyLockType;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.enums.MyLockType.*;

@Component
public class MyLockFactory {


    private Map<MyLockType, Function<String ,RLock>> lockMap;

    public MyLockFactory(RedissonClient redissonClient) {
        //this.lockMap = lockMap = new HashMap<>();
        this.lockMap = new EnumMap<>(MyLockType.class);
        lockMap.put(RE_ENTRANT_LOCK,redissonClient::getLock);
        lockMap.put(FAIR_LOCK,redissonClient::getFairLock);
        lockMap.put(READ_LOCK,key -> redissonClient.getReadWriteLock(key).readLock());
        lockMap.put(WRITE_LOCK,key -> redissonClient.getReadWriteLock(key).writeLock());
    }

    public RLock getLock(MyLockType myLockType, String key) {
        return lockMap.get(myLockType).apply(key);
    }
}
