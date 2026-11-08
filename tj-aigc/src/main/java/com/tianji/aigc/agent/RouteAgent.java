package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.service.IChatSessionService;
import com.tianji.aigc.utils.RedisHashUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RouteAgent extends AbstractAgent{

    private final SystemPromptConfig systemPromptConfig;

    public RouteAgent(IChatSessionService chatSessionService,
                      ChatClient chatClient,
                      StringRedisTemplate redisTemplate,
                      ChatMemory chatMemory,
                      RedisHashUtil redisHashUtil,
                      SystemPromptConfig systemPromptConfig) {
        super(chatSessionService, chatClient, redisTemplate, chatMemory, redisHashUtil);
        this.systemPromptConfig = systemPromptConfig;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public String systemMessage() {
        return systemPromptConfig.getRouteAgentSystemMessage().get();
    }
}
