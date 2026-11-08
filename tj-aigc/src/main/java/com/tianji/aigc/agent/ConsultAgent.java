package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.service.IChatSessionService;
import com.tianji.aigc.tools.CourseTool;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.common.utils.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConsultAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final VectorStore vectorStore;
    private final CourseTool courseTool;

    public ConsultAgent(IChatSessionService chatSessionService,
                        ChatClient chatClient,
                        StringRedisTemplate redisTemplate,
                        ChatMemory chatMemory,
                        RedisHashUtil redisHashUtil,
                        SystemPromptConfig systemPromptConfig,
                        VectorStore vectorStore,
                        CourseTool courseTool) {
        super(chatSessionService, chatClient, redisTemplate, chatMemory, redisHashUtil);
        this.systemPromptConfig = systemPromptConfig;
        this.vectorStore = vectorStore;
        this.courseTool = courseTool;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.CONSULT;
    }

    @Override
    public String systemMessage() {
        return systemPromptConfig.getConsultAgentSystemMessage().get();
    }

    @Override
    public List<Advisor> advisors() {
        return List.of(QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.6).topK(6).build())
                .build());
    }

    @Override
    public Object[] tools() {
        return new Object[]{courseTool};
    }

    @Override
    public Map<String, Object> toolContexts(String sessionId, String requestId) {
        var userId = UserContext.getUser();
        return Map.of(
                Constant.USER_ID, userId, // 设置用户id参数
                Constant.REQUEST_ID, requestId  // 设置请求id参数
        );
    }
}
