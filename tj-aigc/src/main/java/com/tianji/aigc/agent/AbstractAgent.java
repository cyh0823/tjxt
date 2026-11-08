package com.tianji.aigc.agent;

import cn.hutool.core.util.IdUtil;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.IChatService;
import com.tianji.aigc.service.IChatSessionService;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
public abstract class AbstractAgent implements Agent{

    private final IChatSessionService chatSessionService;
    private final ChatClient chatClient;
    private final StringRedisTemplate redisTemplate;
    private final ChatMemory chatMemory;
    private final RedisHashUtil redisHashUtil;

    private static final String GENERATE_STATUS_PREFIX = "GENERATE_STATUS";
    private static final Duration GENERATE_STATUS_TTL = Duration.of(5, ChronoUnit.MINUTES);
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    @Override
    public String process(String question, String sessionId){
        Long userId = UserContext.getUser();
        String requestId = generateRequestId();

        chatSessionService.update(sessionId, question, userId);
        return getChatClientRequest(question, sessionId, requestId)
                .call()
                .content();
    }

    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId) {
        // 保证一个会话不能同时处理两个问题
        String statusKey = GENERATE_STATUS_PREFIX + ":" + sessionId;
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(statusKey, "1", GENERATE_STATUS_TTL))) {
            throw new BadRequestException("该会话正在生成回答，请先停止或等待完成");
        }

        Long userId = UserContext.getUser();
        String requestId = generateRequestId();
        StringBuffer output = new StringBuffer();
        String conversationId = IChatService.getConversationId(sessionId);

        chatSessionService.update(sessionId, question, userId);

        return getChatClientRequest(question, sessionId, requestId)
                .stream()
                .chatResponse()
                .doOnError(throwable -> redisTemplate.delete(statusKey))
                .doOnComplete(() -> redisTemplate.delete(statusKey))
                .doOnCancel(() -> {
                    // 取消时删除sessionId
                    redisTemplate.delete(sessionId);
                    chatMemory.add(conversationId, new AssistantMessage(output.toString()));
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
                    output.append(text);
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
    public void stop(String sessionId){
        redisTemplate.delete(GENERATE_STATUS_PREFIX + ":" + sessionId);
    }

    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId){
        String conversationId = IChatService.getConversationId(sessionId);
        return Map.of(ChatMemory.CONVERSATION_ID, conversationId);
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequest(String question, String sessionId, String requestId) {
        return chatClient.prompt()
                .system(promptSystemSpec -> promptSystemSpec.text(this.systemMessage()).params(this.systemMessageParams()))
                .advisors(advisor -> advisor.advisors(this.advisors()).params(this.advisorParams(sessionId, requestId)))
                .tools(this.tools())
                .toolContext(this.toolContexts(sessionId, requestId))
                .user(question);
    }

    public String generateRequestId() {
        return IdUtil.fastSimpleUUID();
    }
}
