package com.tianji.learning.service.impl;

import com.tianji.api.client.aigc.AigcClient;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.service.AIService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {
    @Value("${tj.ai.user-id:9999}")
    private Long AIUserId;

    private final AigcClient aigcClient;
    private final IInteractionReplyService interactionReplyService;

    @Async
    @Override
    public void autoReply(InteractionQuestion interactionQuestion) {
        String question = StringUtils.format("""
                这是一个学生提出的问题，请以专业的角度进行回答，不要随意编造。
                标题：{} 。
                描述：{} 。""", interactionQuestion.getTitle(), interactionQuestion.getDescription());

        // 设置当前用户id，否在会出现401错误
        UserContext.setUser(interactionQuestion.getUserId());
        String reply = aigcClient.chatText(question);

        // 构建系统自动回复数据对象
        var replyDTO = ReplyDTO.builder()
                .userId(AIUserId)          // 固定系统用户ID
                .content(reply)         // AI生成的回复内容
                .anonymity(false)       // 明确显示系统回复身份
                .questionId(interactionQuestion.getId())  // 关联原始问题ID
                .isStudent(false)       // 标记为非学生回复
                .build();

        // 持久化存储生成的回复
        this.interactionReplyService.saveReply(replyDTO);
    }
}
