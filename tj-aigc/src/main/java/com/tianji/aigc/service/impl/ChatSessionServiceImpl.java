package com.tianji.aigc.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.domain.po.ChatSession;
import com.tianji.aigc.domain.vo.ChatSessionVO;
import com.tianji.aigc.domain.vo.MessageVO;
import com.tianji.aigc.domain.vo.SessionVO;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.service.IChatService;
import com.tianji.aigc.service.IChatSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 对话session 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-09-07
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    private final SessionProperties sessionProperties;
    private final ChatMemory chatMemory;

    @Override
    public SessionVO createSession(Integer num) {
        if (num > 10) {
            throw new BadRequestException("问题数不能大于10");
        }
        SessionVO vo = BeanUtils.copyBean(sessionProperties, SessionVO.class);

        // 从十个问题中随机选n个问题
        vo.setExamples(RandomUtils.randomEleList(sessionProperties.getExamples(), num));

        // 根据UUID生成sessionID
        vo.setSessionId(IdUtil.fastSimpleUUID());

        // 持久化到数据库
        ChatSession chatSession = ChatSession.builder()
                .sessionId(vo.getSessionId())
                .userId(UserContext.getUser())
                .build();

        save(chatSession);

        return vo;
    }

    @Override
    public List<SessionVO.Example> getHotSession(Integer num) {
        return RandomUtils.randomEleList(sessionProperties.getExamples(), num);
    }

    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        String conversationId = IChatService.getConversationId(sessionId);
        List<Message> messages = chatMemory.get(conversationId);
        return StreamUtil.of(messages)
                .filter(message -> message.getMessageType() == MessageType.ASSISTANT || message.getMessageType() == MessageType.USER)
                .map(message -> MessageVO.builder()
                            .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                            .content(message.getText())
                            .build())
                .toList();
    }

    @Async
    @Override
    public void update(String sessionId, String title, Long userId) {
        // 会话时为会话设置标题
        ChatSession chatSession = lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .one();
        if (chatSession == null) {
            return;
        }
        if (StringUtils.isEmpty(chatSession.getTitle()) && !StringUtils.isEmpty(title)) {
            chatSession.setTitle(StringUtils.sub(title, 0, 100));
        }
        chatSession.setUpdateTime(LocalDateTime.now());
        updateById(chatSession);
    }

    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        Long userId = UserContext.getUser();

        List<ChatSessionVO> historySessions = BeanUtils.copyList(
                lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT 30")
                .list(), ChatSessionVO.class);
        if (historySessions.isEmpty()) {
            log.error("用户{}历史消息列表为空", userId);
            return Map.of();
        }

        final String TODAY = "当天";
        final String LAST_30_DAYS = "最近30天";
        final String RECENT_YEAR = "最近一年";
        final String OVER_YEAR = "超过一年";

        LocalDate today = LocalDateTime.now().toLocalDate();

        return CollStreamUtil.groupByKey(historySessions, historySession -> {
            long days = Math.abs(ChronoUnit.DAYS.between(historySession.getUpdateTime(), today));
            if (days == 0) {
                return TODAY;
            } else if (days <= 30) {
                return LAST_30_DAYS;
            } else if (days <= 365) {
                return RECENT_YEAR;
            } else {
                return OVER_YEAR;
            }
        });
    }

    @Override
    public void deleteById(String sessionId) {
        boolean removed = remove(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser()));
        if (!removed) {
            throw new DbException("删除会话记忆失败");
        }
        String conversationId = IChatService.getConversationId(sessionId);
        chatMemory.clear(conversationId);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        if (StrUtil.isBlank(title)) {
            throw new BadRequestException("传入的参数不能为空");
        }

        lambdaUpdate()
                .set(ChatSession::getTitle, title)
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser())
                .update();
    }
}
