package com.tianji.promotion.enums;

import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.promotion.utils.MyLock;
import org.redisson.api.RLock;

public enum MyLockStrategy {
    SKIP_FAST {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(0, prop.leaseTime(), prop.unit());
        }
    },
    FAIL_FAST {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean b = lock.tryLock(0, prop.leaseTime(), prop.unit());
            if (!b) {
                throw new BizIllegalException("获取锁失败");
            }
            return true;
        }
    },
    KEEP_TRYING {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            lock.lock(prop.leaseTime(),prop.unit());
            return true;
        }
    },
    SKIP_AFTER_RETRY_TIMEOUT {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
        }
    },
    FAIL_AFTER_RETRY_TIMEOUT {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean b = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            if (!b) {
                throw new BizIllegalException("获取锁失败");
            }
            return true;
        }
    },
    ;

    public abstract boolean tryLock(RLock lock, MyLock prop) throws InterruptedException;
}
