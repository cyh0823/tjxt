package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-22
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService interactionQuestionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReplyOrAnswer(ReplyDTO replyDTO) {
        // A:hadfihdiuadh?(问题)
        // B:iahisfh(回答)
        // C评论B:hdhiash(评论)
        if (BeanUtils.isEmpty(replyDTO)) {
            return;
        }
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        // 设置基本参数
        reply.setUserId(UserContext.getUser());
        // 设置评论数量和点赞数量为0   默认值就是0
        /*reply.setReplyTimes(0);
        reply.setLikedTimes(0);*/
        boolean save = save(reply);
        if (!save) {
            throw new BadRequestException("发送评论或回答失败");
        }
        InteractionQuestion question = interactionQuestionService.getById(reply.getQuestionId());
        if (BeanUtils.isEmpty(question)) {
            // 如果查询不到问题，抛出异常
            throw new BadRequestException("问题不存在");
        }
        if (reply.getAnswerId() == null) {
            // 如果是对问题回答，更新问题的最近回答id和回答次数
            boolean update = interactionQuestionService.lambdaUpdate()
                    .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                    .setSql("answer_times = answer_times + 1")
                    .eq(InteractionQuestion::getId, reply.getQuestionId())
                    .update();
            if (!update) {
                throw new BadRequestException("更新问题回答失败");
            }
        }else {
            // 如果是对回答的评论，更新目标回复的评论次数
            boolean update = lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId,reply.getTargetReplyId())
                    .update();
            if (!update) {
                throw new BadRequestException("更新目标回复评论次数失败");
            }
        }
        // 如果是学生的评论，且问题的状态为已查看需要更新问题状态为未查看
        if (replyDTO.getIsStudent() && question.getStatus() == QuestionStatus.CHECKED) {
            // 更新问题状态为未查看
            boolean update = interactionQuestionService.lambdaUpdate()
                    .set(InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
                    .eq(InteractionQuestion::getId, reply.getQuestionId())
                    .update();
            if (!update) {
                throw new BadRequestException("更新问题状态失败");
            }
        }
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        // 0.判一下query是否为空
        if (BeanUtils.isEmpty(query)) {
            throw new BadRequestException("查询参数不能为空");
        }
        // 1.问题与回答至少指定一个
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            throw new BadRequestException("问题与回答至少指定一个");
        }
        // 2.分页查询
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.转为VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            voList.add(vo);
            // 填充回复者字段
            Long userId = record.getUserId();
            InteractionReply replier = getById(userId);
            if (BeanUtils.isNotEmpty(replier)) {

            }
            // TODO 不会力

        }
        return null;
    }
}
