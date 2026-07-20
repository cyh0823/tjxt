package com.tianji.promotion.config;

import com.tianji.promotion.utils.MyLockAspect;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PromotionConfig {

    @Bean
    public Executor generateExchangeCodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程池数
        executor.setCorePoolSize(2);
        // 最大线程池数
        executor.setMaxPoolSize(5);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名称前缀
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /*@Bean
    public MyLockAspect myLockAspect(RedissonClient redissonClient) {
        return new MyLockAspect(redissonClient);
    }*/

    @Bean
    public Executor discountSolutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(9999);
        executor.setThreadNamePrefix("discount-solution-handler-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
