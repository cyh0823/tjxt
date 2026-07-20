package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
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
    private final UserClient userClient;
    private RabbitMqHelper mqHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReplyOrAnswer(ReplyDTO replyDTO) {
        // A:hadfihdiuadh?(问题)
        // B:iahisfh(回答)
        // C评论B:hdhiash(评论)
        // answerId：当前回复归属的顶层父级
        // targetReplyId：标记当前回复直接回复的对象

        // 如果传过来的数据中没有问题id或者没有回复的内容，直接返回
        if (BeanUtils.isEmpty(replyDTO) || replyDTO.getQuestionId() == null || replyDTO.getContent() == null) {
            throw new BadRequestException("请求存在问题");
        }
        // 根据问题id查询问题，如果问题不存在，直接抛出异常
        InteractionQuestion question = interactionQuestionService.getById(replyDTO.getQuestionId());
        if (BeanUtils.isEmpty(question) || question.getHidden()) {
            // 如果查询不到问题，或问题已被隐藏，抛出异常
            throw new BadRequestException("问题不存在");
        }
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        // 设置基本参数
        Long userId = UserContext.getUser();
        reply.setUserId(userId);
        // 设置评论数量和点赞数量为0   默认值就是0
        /*reply.setReplyTimes(0);
        reply.setLikedTimes(0);*/
        boolean save = save(reply);
        if (!save) {
            throw new BizIllegalException("发送评论或回答失败");
        }

        if (reply.getAnswerId() == null) {
            // 如果是对问题回答，更新问题的最近回答id和回答次数
            boolean update = interactionQuestionService.lambdaUpdate()
                    .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                    .setSql("answer_times = answer_times + 1")
                    .eq(InteractionQuestion::getId, reply.getQuestionId())
                    .update();
            if (!update) {
                throw new BizIllegalException("更新问题回答失败");
            }
            // 如果是学生回答的,发送消息给学生添加积分
            mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,MqConstants.Key.WRITE_REPLY,userId);
        }else {
            // 如果是对回答的评论，更新目标回复的评论次数
            if (reply.getTargetReplyId() != null) {
                // 健壮性判断
                boolean update = lambdaUpdate()
                        .setSql("reply_times = reply_times + 1")
                        .eq(InteractionReply::getId,reply.getAnswerId())
                        .update();
                if (!update) {
                    throw new BizIllegalException("更新目标回复评论次数失败");
                }
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
                throw new BizIllegalException("更新问题状态失败");
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
        Long answerId = query.getAnswerId();
        Long questionId = query.getQuestionId();
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 如果是根据问题id的分页查询，说明是查询问题下的一级回答
        // 如果是根据回答id的分页查询，说明是查询回答下的所有评论，这两者都可以按照时间排序
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO replyVO = BeanUtils.copyBean(record, ReplyVO.class);
            if (!record.getAnonymity()) {
                // 如果不是匿名用户，需要获取用户的昵称以及头像
                UserDTO userDTO = userClient.queryUserById(record.getUserId());
                replyVO.setUserName(userDTO.getName());
                replyVO.setUserIcon(userDTO.getIcon());
            }
            if(answerId != null) {
                // 如果是根据回答id的分页查询
                InteractionReply targetReply = getById(record.getTargetReplyId());
                if (targetReply.getAnonymity()) {
                    // 如果上一级回答的用户是匿名的，那么设置被评论的用户昵称为“匿名用户”
                    replyVO.setTargetUserName("匿名用户");
                } else {
                    UserDTO targetUser = userClient.queryUserById(record.getTargetUserId());
                    replyVO.setTargetUserName(targetUser.getUsername());
                }
            }
            // 如果是根据问题id的分页查询，不需要被评论的用户昵称，直接添加到voList中即可
            voList.add(replyVO);
        }
        return PageDTO.of(page, voList);
    }
}
