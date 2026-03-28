package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
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
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Override
    public void receiveCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券已过期或尚未开始发放");
        }
        if (coupon.getTotalNum() <= coupon.getIssueNum()) {
            throw new BadRequestException("优惠券已发放完");
        }
        Long userId = UserContext.getUser();
        synchronized (userId.toString().intern()) {
            // 校验并生成优惠券
            // 获取当前类的 Spring 代理对象
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            // 用代理对象调用 → 事务生效！
            userCouponService.checkAndSave(userId, coupon);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exchangeCoupon(String code) {
        long serialNum = CodeUtil.parseCode(code);
        boolean marked = codeService.updateExchangeMark(serialNum,true);
        if (marked) {
            throw new BadRequestException("兑换码已使用或不存在");
        }
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
            checkAndSave(userId,coupon);
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
    public void checkAndSave(Long userId, Coupon coupon) {
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, userId)
                .count();
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("已超过最大领取优惠券次数");
        }
        // 添加已领取数
        int i = couponMapper.incrIssueNum(coupon.getId());
        if (i == 0) {
            throw new BizIllegalException("优惠券已发放完");
        }
        // 添加用户领取记录
        save(userId, coupon);
    }
}
