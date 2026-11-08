package com.tianji.aigc.agent;

import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.domain.vo.ChatSessionVO;
import com.tianji.aigc.enums.AgentTypeEnum;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface Agent {
    // 对话相关接口
    String process(String question, String sessionId);
    Flux<ChatEventVO> processStream(String question, String sessionId);
    AgentTypeEnum getAgentType();
    void stop(String sessionId);
    default String systemMessage(){
        return "";
    }

    // 工具相关接口
    Object[] EMPTY_TOOL = new Object[0];

    default Object[] tools(){
        return EMPTY_TOOL;
    }

    default Map<String, Object> toolContexts(String sessionId, String requestId){
        return Map.of();
    }

    default List<Advisor> advisors(){
        return List.of();
    }

    default Map<String, Object> advisorParams(String sessionId, String requestId){
        return Map.of();
    }

    default Map<String, Object> systemMessageParams() {
        return Map.of();
    }
}
