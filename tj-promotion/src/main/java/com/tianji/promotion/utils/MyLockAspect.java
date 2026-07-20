package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@RequiredArgsConstructor
@Component
public class MyLockAspect implements Ordered {

    private final MyLockFactory lockFactory;

    // 环绕通知：匹配【所有加了@MyLock注解的方法】
    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        String key = myLock.name();
        RLock lock = lockFactory.getLock(myLock.lockType(),myLock.name());
        boolean isLock = myLock.strategy().tryLock(lock,myLock);
        if (!isLock) {
            // 直接结束（异常逻辑已经被处理了）
            return null;
        }
        try {
            return pjp.proceed();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        // 设置优先级，保证事务执行完之后在释放锁
        // 默认状态下，所有Aspect的优先级都是Ordered.LOWEST_PRECEDENCE，所以这里设置为0
        return 0;

    }
}
