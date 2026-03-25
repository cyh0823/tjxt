package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {

        // 1.向redis中添加签到记录
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        long offset = now.getDayOfMonth() - 1;
        Boolean signed = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(signed)) {
            throw new BizIllegalException("不允许重复签到");
        }
        // 2，记录连续签到的天数
        int signDays = countSignDays(key,now.getDayOfMonth());
        // 3.计算签到得分
        int rewardPoints = 0;
        switch (signDays) {
            case 7 :
                rewardPoints = 10;
                break;
            case 14 :
                rewardPoints = 20;
                break;
            case 28 :
                rewardPoints = 40;
                break;
        }
        // 4.保存积分明细
        // 使用Mq发送消息保存记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId,rewardPoints + 5));
        // 5.封装VO返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public List<Integer> getSignDays() {
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        int daysOfMonth = now.getDayOfMonth();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);

        List<Long> result = redisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands
                                .BitFieldType.unsigned(daysOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return null;
        }
        List<Integer> signDays = new ArrayList<>(daysOfMonth);
        int num = result.get(0).intValue();
        while (num > 0) {
            signDays.add(num % 2);
            num /= 2;
        }

        CollUtils.reverse(signDays);
        return signDays;
    }

    private int countSignDays(String key, int dayOfMonth) {
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands
                                .BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int l = result.get(0).intValue();
        int count = 0;
        if (l == 0) {
            return 0;
        }
        while (l % 2 == 0){
            l /= 2;
            count++;
        }
        return count;
    }
}
