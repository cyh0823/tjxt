package com.tianji.promotion.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.mq.message.ChangeStatusMessage;
import com.tianji.promotion.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponListener {

    private final ICouponService couponService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "coupon.change.status", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.PROMOTION_EXCHANGE, type = "topic"),
            key = MqConstants.Key.COUPON_RECEIVE
    ))
    public void ChangeCouponStatus(ChangeStatusMessage message){
        Coupon coupon = couponService.getById(message.getCouponId());
        if (message.isBegin()) {
            coupon.setStatus(CouponStatus.ISSUING);
        } else {
            coupon.setStatus(CouponStatus.FINISHED);
        }
        couponService.updateById(coupon);
    }
}
