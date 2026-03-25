package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningPointsListener {
    private final IPointsRecordService pointsRecordService;

    // 监听写回答得分
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.points.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = "topic"),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId) {
        pointsRecordService.addPointsRecord(userId,5, PointsRecordType.QA);
    }

    // 监听签到得分
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "sign.points.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = "topic"),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage signInMessage) {
        pointsRecordService.addPointsRecord(signInMessage.getUserId(), signInMessage.getPoints(), PointsRecordType.SIGN);
    }

    // 监听学习章节得分
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.points.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = "topic"),
            key = MqConstants.Key.LEARN_SECTION
    ))
    public void listenLearningSectionMessage(SignInMessage signInMessage) {
        pointsRecordService.addPointsRecord(signInMessage.getUserId(), 10, PointsRecordType.LEARNING);
    }

    // 监听写笔记得分
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "write.note.points.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = "topic"),
            key = MqConstants.Key.WRITE_NOTE
    ))
    public void listenWriteNoteMessage(SignInMessage signInMessage) {
        pointsRecordService.addPointsRecord(signInMessage.getUserId(), 3, PointsRecordType.NOTE);
    }

    // 监听笔记被采集得分
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "gather.note.points.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = "topic"),
            key = MqConstants.Key.NOTE_GATHERED
    ))
    public void listenGatherNoteGatheredMessage(SignInMessage signInMessage) {
        pointsRecordService.addPointsRecord(signInMessage.getUserId(), 2, PointsRecordType.NOTE);
    }
}
