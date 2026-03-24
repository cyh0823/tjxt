package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikeTimesDto;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikedTimesChangeListener {

    private final IInteractionReplyService interactionReplyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimes(List<LikeTimesDto> likeTimesDtos) {
        log.info("监听到点赞的消息");
        List<InteractionReply> list = new ArrayList<>(likeTimesDtos.size());
        for (LikeTimesDto dto : likeTimesDtos) {
            InteractionReply r = new InteractionReply();
            r.setId(dto.getBizId());
            r.setLikedTimes(dto.getLikeTimes());
        }
        interactionReplyService.updateBatchById(list);
    }
}
