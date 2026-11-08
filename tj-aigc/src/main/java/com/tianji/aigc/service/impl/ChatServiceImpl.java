package com.tianji.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.domain.po.ChatSession;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.mapper.ChatMapper;
import com.tianji.aigc.service.IChatService;
import com.tianji.aigc.service.IChatSessionService;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ENHANCE")
public class ChatServiceImpl extends ServiceImpl<ChatMapper, ChatSession> implements IChatService {

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final ChatMemory chatMemory;
    private final StringRedisTemplate redisTemplate;
    private final RedisHashUtil redisHashUtil;
    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder()
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();
    private final VectorStore vectorStore;
    private final IChatSessionService chatSessionService;

    private static final String GENERATE_STATUS_PREFIX = "GENERATE_STATUS";
    private static final Duration GENERATE_STATUS_TTL = Duration.of(5, ChronoUnit.MINUTES);

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 保证一个会话不能同时处理两个问题
        String statusKey = GENERATE_STATUS_PREFIX + ":" + sessionId;
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(statusKey, "1", GENERATE_STATUS_TTL))) {
            throw new BadRequestException("该会话正在生成回答，请先停止或等待完成");
        }

        String conversationId = IChatService.getConversationId(sessionId);
        StringBuffer outputBuilder = new StringBuffer();
        String requestId = IdUtil.fastSimpleUUID();
        Long userId = UserContext.getUser();

        // 异步更新会话标题
        chatSessionService.update(sessionId, question, userId);

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.6).topK(6).build())
                .build();

        return chatClient
                .prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                        .param("now", DateUtil.now()) // 设置当前时间的参数
                )
                .advisors(advisor -> advisor
                        .advisors(qaAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId))
                .user(question)
                .stream()
                .chatResponse()
                .doOnError(throwable -> redisTemplate.delete(statusKey))
                .doOnComplete(() -> redisTemplate.delete(statusKey))
                .doOnCancel(() -> {
                    chatMemory.add(conversationId, new AssistantMessage(outputBuilder.toString()));
                })
                .takeWhile(response -> { // 通过返回值来确认Flux是否继续：true继续，false终止
                    return redisTemplate.opsForValue().get(statusKey) != null;
                })
                .map(chatResponse -> {
                    // 实现将ToolResult存入记忆中
                    // 将requestId存入Redis中，选择metadata中的id作为key
                    String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if (StringUtils.equals(finishReason, Constant.STOP)) {
                        String messageId = chatResponse.getMetadata().getId();
                        redisTemplate.opsForValue().set(Constant.Prefix.MEMORY_PREFIX + messageId, requestId);
                    }
                    // 获取大模型的输出的内容
                    String text = chatResponse.getResult().getOutput().getText();
                    // 将输出封装到outputBuilder
                    outputBuilder.append(text);
                    // 封装响应对象
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    List<ChatEventVO> chatEventVOs = redisHashUtil.buildAllEventVOs(requestId);
                    if (CollUtils.isEmpty(chatEventVOs)) {
                        return Flux.just(STOP_EVENT);
                    }
                    redisHashUtil.delete(requestId);
                    chatEventVOs.add(STOP_EVENT);
                    return Flux.fromIterable(chatEventVOs);
                }
                ));
    }

    @Override
    public void stop(String sessionId) {
        // 停止时通过中断Flux流程完成的，中断后Spring AI不会触发ChatMemory的saveAll保存消息
        redisTemplate.delete(GENERATE_STATUS_PREFIX + ":" + sessionId);
    }

    @Override
    public String chatText(String question) {
        return chatClient
                .prompt()
                .system(systemPromptConfig.getTextSystemMessage().get())
                .user(question)
                .call()
                .content();
    }
}
