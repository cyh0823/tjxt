package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.service.IChatSessionService;
import com.tianji.aigc.tools.OrderTools;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.common.utils.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyAgent extends AbstractAgent{

    private final SystemPromptConfig systemPromptConfig;
    private final OrderTools orderTools;

    public BuyAgent(
            IChatSessionService chatSessionService,
            ChatClient chatClient,
            StringRedisTemplate redisTemplate,
            ChatMemory chatMemory,
            RedisHashUtil redisHashUtil,
            SystemPromptConfig systemPromptConfig,
            OrderTools orderTools) {
        super(chatSessionService, chatClient, redisTemplate, chatMemory, redisHashUtil);
        this.systemPromptConfig = systemPromptConfig;
        this.orderTools = orderTools;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.BUY;
    }

    @Override
    public String systemMessage() {
        return systemPromptConfig.getBuyAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{orderTools};
    }

    @Override
    public Map<String, Object> toolContexts(String sessionId, String requestId) {
        Long userId = UserContext.getUser();
        return Map.of(
                Constant.USER_ID, userId,
                Constant.REQUEST_ID, requestId
        );
    }
}
