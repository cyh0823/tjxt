package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.domain.po.ChatSession;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.generator.TextGenerator;
import com.tianji.aigc.mapper.ChatMapper;
import com.tianji.aigc.service.IChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
public class AgentServiceImpl extends ServiceImpl<ChatMapper, ChatSession> implements IChatService {

    private final TextGenerator textGenerator;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        String routeResult = findAgentByType(AgentTypeEnum.ROUTE).process(question, sessionId);
        Agent agent = findAgentByType(AgentTypeEnum.valueOf(routeResult));

        if (agent == null) {
            ChatEventVO chatEventVO = ChatEventVO.builder()
                    .eventData(routeResult)
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .build();
            return Flux.just(chatEventVO, AbstractAgent.STOP_EVENT);
        }
        return agent.processStream(question, sessionId);
    }

    @Override
    public void stop(String sessionId) {
        findAgentByType(AgentTypeEnum.ROUTE).stop(sessionId);
    }

    @Override
    public String chatText(String question) {
        return textGenerator.generate(question);
    }

    private Agent findAgentByType(AgentTypeEnum agentType) {
        if (agentType == null) {
            return null;
        }
        Map<String, Agent> beans = SpringUtil.getBeansOfType(Agent.class);
        for (Agent agent : beans.values()) {
            if (agent.getAgentType() == agentType) {
                return agent;
            }
        }
        return null;
    }
}
