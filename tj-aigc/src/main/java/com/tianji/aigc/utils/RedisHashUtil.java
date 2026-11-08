package com.tianji.aigc.utils;

import cn.hutool.json.JSONUtil;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.tools.strategy.ToolResultStrategy;
import com.tianji.common.utils.CollUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 实现课程卡片的生成（将tool返回给大模型的信息存储在redis中）
 */
@Component
@RequiredArgsConstructor
public class RedisHashUtil {

    private final StringRedisTemplate redisTemplate;
    private final List<ToolResultStrategy> toolResultStrategies;   // Spring自动注入所有策略

    /** 存对象到 hash 的某 field（自动 JSON 化） */
    public void hset(String key, String field, Object value, long timeout, TimeUnit unit) {
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);
        hashOps.put(field, JSONUtil.toJsonStr(value));
        hashOps.expire(timeout, unit);
    }

    /** 取 hash 某 field，反序列化成对象 */
    public ChatEventVO buildEventVO(String key, String field) {
        Object raw = redisTemplate.opsForHash().get(key, field);
        if (raw == null) {
            return null;
        }
        return toolResultStrategies.stream()
                .filter(s -> s.support(field))   // 获取对应策略
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无法匹配策略" + field))
                .buildEventVO((String) raw);
    }

    /** 取整个 hash，反序列化成 Map<field, 对象> */
    public List<ChatEventVO> buildAllEventVOs(String key) {
        Map<Object, Object> entries = redisTemplate.boundHashOps(key).entries();
        if (entries.isEmpty()) {
            return CollUtils.emptyList();
        }
        return entries.keySet().stream()
                .map(field -> buildEventVO(key, (String) field))
                .collect(Collectors.toList());
    }

    /** 删某 key 下的若干 field */
    public void hdel(String key, String... fields) {
        redisTemplate.boundHashOps(key).delete(Arrays.asList(fields));
    }

    /** 删整个 key */
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
