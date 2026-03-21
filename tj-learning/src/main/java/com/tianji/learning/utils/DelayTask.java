package com.tianji.learning.utils;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
public class DelayTask<D> implements Delayed {

    private D data;
    // 延时任务执行时间
    private long deadlineNanos;

    public DelayTask(Duration delayTime, D data) {
        this.deadlineNanos = delayTime.toNanos() + System.nanoTime();
        this.data = data;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // 返回任务剩余延时时间 只有当这个值为0的时候才会被DelayQueue取到
        return unit.convert(Math.max(0,deadlineNanos - System.nanoTime()),TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        long l = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        if (l > 0){
            return 1;
        } else if (l < 0) {
            return -1;
        }else {
            return 0;
        }
    }
}
