package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.constant.Constant;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisChatMemoryRepository implements MyChatMemoryRepository {

    public static final String DEFAULT_PREFIX = "CHAT:";

    private final String prefix;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository(){
        this.prefix = DEFAULT_PREFIX;
    }

    public RedisChatMemoryRepository(String prefix){
        this.prefix = prefix;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = this.stringRedisTemplate.keys(this.prefix + "*");
        if (CollUtils.isEmpty(keys)) {
            return List.of();
        }
        return StreamUtil.of(keys)
                .map(key -> StrUtil.replace(key, this.prefix, ""))
                .toList();
    }


    @Override
    public List<Message> findByConversationId(String conversationId) {
        String redisKey = getKey(conversationId);
        BoundListOperations<String, String> listOps = stringRedisTemplate.boundListOps(redisKey);
        List<String> messages = listOps.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "消息列表不能为空");
        String redisKey = this.getKey(conversationId);
        BoundListOperations<String, String> listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 保存数据时，会传入全部的消息数据，包括之前的数据，所以需要先删除之前的数据，再添加新的数据
        this.deleteByConversationId(conversationId);
        // 将消息序列化并添加到Redis列表的右侧
        messages.forEach(message -> {
            Map<String, Object> params = null;
            if (message instanceof AssistantMessage assistantMessage) {
                // 从redis中读数据出来
                String messageId = StringUtils.toString(assistantMessage.getMetadata().get(Constant.ID));
                String requestId = stringRedisTemplate.opsForValue().get(Constant.Prefix.MEMORY_PREFIX + messageId);
                if (StringUtils.isNotBlank(requestId)) {
                    BoundHashOperations<String, String, Object> hashOps = stringRedisTemplate.boundHashOps(requestId);
                    params = hashOps.entries();
                    // 写法二：
                    //HashOperations<String, String, Object> stringObjectObjectHashOperations = stringRedisTemplate.opsForHash();
                    //params = stringObjectObjectHashOperations.entries(requestId);
                    // 写法三：
                    // params = stringRedisTemplate.<String, Object>opsForHash().entries(requestId);
                }
            }
            listOps.rightPush(MessageUtil.toJson(message, params));
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }

    @Override
    public void optimization(String conversationId) {
        String redisKey = getKey(conversationId);
        stringRedisTemplate.opsForList().rightPop(redisKey, 2);
    }
}
