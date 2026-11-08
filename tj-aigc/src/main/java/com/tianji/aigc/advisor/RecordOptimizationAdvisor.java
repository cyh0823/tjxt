package com.tianji.aigc.advisor;

import cn.hutool.core.map.MapUtil;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;

@RequiredArgsConstructor
public class RecordOptimizationAdvisor implements BaseAdvisor {
    // 删去Redis中有关RouteAgent的对话记忆
    private final MyChatMemoryRepository chatMemoryRepository;

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 获取响应内容
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        if (chatResponse != null) {
            String text = chatResponse.getResult().getOutput().getText();
            AgentTypeEnum agentTypeEnum = AgentTypeEnum.of(text);
            // 如果生成的内容可以转化成AgentTypeEnum，说明是RouteAgent生成的内容
            if (agentTypeEnum != null) {
                // 获取会话ID
                String conversationId = MapUtil.getStr(chatClientResponse.context(), ChatMemory.CONVERSATION_ID);
                chatMemoryRepository.optimization(conversationId);
            }
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }
}
