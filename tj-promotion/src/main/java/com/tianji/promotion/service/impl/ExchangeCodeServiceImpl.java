package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstant;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstant.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstant.COUPON_CODE_SERIAL_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-27
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;
    private BoundValueOperations<String, String> serialOps;

    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.serialOps = redisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateCode(Coupon coupon) {
        Integer total = coupon.getTotalNum();

        // 获取自增序列号
        Long result = serialOps.increment(total);
        if (result == null) {
            return;
        }

        int maxSerialNum = result.intValue();

        List<ExchangeCode> list = new ArrayList<>(total);
        for (int serialNum = maxSerialNum - total + 1; serialNum <= maxSerialNum; serialNum++) {
            // 异步生成兑换码

            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());
            exchangeCode.setId(serialNum);
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());
            list.add(exchangeCode);
        }
        saveBatch(list);

        // 写入Redis缓存
        redisTemplate.opsForZSet().add(PromotionConstant.COUPON_CODE_RANGE_KEY, coupon.getId().toString(), total);
    }

    @Override
    public boolean updateExchangeMark(long serialNum, boolean mark) {
        // 寻找并设置第serialNum位的bit值
        Boolean b = redisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, mark);
        return b != null && b;
    }
}
