package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-28
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {


    List<Coupon> queryMyCoupons(Long user);
}
