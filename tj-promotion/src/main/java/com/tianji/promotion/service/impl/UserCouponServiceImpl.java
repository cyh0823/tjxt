package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstant;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-28
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    @Lock(name = "lock:coupon:#{couponId}")  // SPEL表达式
    public void receiveCoupon(Long couponId) {
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券已过期或尚未开始发放");
        }
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券已发放完");
        }
        Long userId = UserContext.getUser();
        /*String key = "lock:coupon:uid:" + userId;
        // 创建锁对象
        RLock lock = redissonClient.getLock(key);
        boolean b = lock.tryLock();
        if (!b) {
            throw new BizIllegalException("请求太过频繁");
        }
        try {
            // 校验并生成优惠券
            // 获取当前类的 Spring 代理对象
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            // 用代理对象调用 → 事务生效！
            userCouponService.checkAndSave(userId, coupon);
        } finally {
            // 释放锁
            lock.unlock();
        }*/
        // 用户领取优惠券
        String key = PromotionConstant.USER_COUPON_CACHE_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        if (count > coupon.getUserLimit()) {
            throw new BadRequestException("用户已领取超过最大数量");
        }
        // 扣减优惠券数量
        redisTemplate.opsForHash().increment(
                PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId,
                "totalNum", -1);
        /*IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        userCouponService.checkAndSave(userId, coupon);*/
        // 通过MQ发送消息，更新用户优惠券状态
        mqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                UserCouponDTO.of(userId,couponId));
    }

    private Coupon queryCouponByCache(Long couponId) {
        String key = PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        return BeanUtils.mapToBean(objMap,Coupon.class,false, CopyOptions.create());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exchangeCoupon(String code) {
        long serialNum = CodeUtil.parseCode(code);
        boolean marked = codeService.updateExchangeMark(serialNum,true);
        if (marked) {
            throw new BadRequestException("兑换码已使用或不存在");
        }
        /*// 改造：通过Redis查询兑换信息
        // 1.跟据兑换码的序列号查询对应的优惠券ID
        Set<String> rangeByScore = redisTemplate
                .opsForZSet()
                .rangeByScore(PromotionConstant.COUPON_CODE_RANGE_KEY,
                        serialNum, serialNum + 5000, 0, 1);
        if (rangeByScore == null || rangeByScore.isEmpty()) {
            throw new BizIllegalException("兑换码不存在");
        }
        Long couponId = Long.valueOf(rangeByScore.iterator().next());
        // 2.跟据优惠券Id查询优惠券的信息

        Long issueBeginTime = (Long) redisTemplate.opsForHash().get(PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId, "issueBeginTime");
        Long issueEndTime = (Long) redisTemplate.opsForHash().get(PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId, "issueEndTime");
        Long totalNum = (Long) redisTemplate.opsForHash().get(PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum");
        Integer userLimit = (Integer) redisTemplate.opsForHash().get(PromotionConstant.COUPON_CACHE_KEY_PREFIX + couponId, "userLimit");*/

        try {
            // 查询兑换码
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if (BeanUtils.isEmpty(exchangeCode)) {
                throw new BizIllegalException("兑换码不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已过期");
            }
            // 核销优惠券
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            Long userId = UserContext.getUser();
            checkAndSave(UserCouponDTO.of(userId,coupon.getId()));
            // 更新优惠券状态和使用者
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId,userId)
                    .eq(ExchangeCode::getId,exchangeCode.getId())
                    .update();
        }catch (Exception e){
            codeService.updateExchangeMark(serialNum,false);
            throw e;
        }
    }

    private void save(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        LocalDateTime now = LocalDateTime.now();
        // 设置优惠券的有效期
        // 判断有效期开始字段是否为空，若为空则说明优惠券为领取后有效
        LocalDateTime begin = coupon.getTermBeginTime();
        LocalDateTime end = coupon.getTermEndTime();
        if (begin == null) {
            begin = now;
            end = now.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(begin);
        userCoupon.setTermEndTime(end);
        save(userCoupon);
    }


    @Transactional
    @Override
    public void checkAndSave(UserCouponDTO uc) {
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (BeanUtils.isEmpty(coupon)) {
            throw new BizIllegalException("优惠券不存在");
        }
        /*Long userId = uc.getUserId();
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, userId)
                .count();
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("已超过最大领取优惠券次数");
        }*/
        // 添加已领取数
        int i = couponMapper.incrIssueNum(coupon.getId());
        if (i == 0) {
            throw new BizIllegalException("优惠券已发放完");
        }
        // 添加用户领取记录
        save(uc.getUserId(), coupon);
    }
}
