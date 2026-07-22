package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstant;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mq.message.ChangeStatusMessage;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;
    private final IExchangeCodeService exchangeCodeService;
    private final IUserCouponService userCouponService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public void saveCoupon(CouponFormDTO couponFormDTO) {
        // 1. 保存优惠券信息
        Coupon coupon = BeanUtils.copyProperties(couponFormDTO, Coupon.class);
        save(coupon);
        // 2. 保存优惠券限定范围信息
        if (!couponFormDTO.getSpecific()) {
            // 优惠券没有限定范围
            return;
        }
        Long couponId = coupon.getId();
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new IllegalArgumentException("优惠券限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(list);
    }

    @Override
    public PageDTO<CouponPageVO> couponPage(CouponQuery query) {
        Page<Coupon> page = lambdaQuery()
                .eq(query.getType() != null, Coupon::getType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page,voList);
    }

    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO couponIssueFormDTO) {
        // 校验数据
        LocalDateTime beginTime = couponIssueFormDTO.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = couponIssueFormDTO.getIssueEndTime();
        if (beginTime != null && beginTime.isAfter(endTime)) {
            throw new BadRequestException("结束时间不能在开始时间之前!");
        }
        // 查询优惠券
        Long couponId = couponIssueFormDTO.getId();
        Coupon coupon = getById(couponId);
        if (BeanUtils.isEmpty(coupon)) {
            throw new BadRequestException("优惠券不存在!");
        }
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.UN_ISSUE) {
            throw new BizIllegalException("优惠券状态错误");
        }
        // 判断优惠券是否立刻开放
        boolean isBegin = beginTime == null || !beginTime.isAfter(now);
        // 拷贝属性
        Coupon result = BeanUtils.copyBean(couponIssueFormDTO, Coupon.class);
        if (isBegin) {
            // 立即发放，设置状态为进行中发放
            result.setStatus(CouponStatus.ISSUING);
            result.setIssueBeginTime(now);
            // 设置延时任务：时间到了就将优惠券状态设为已结束
            task(now, endTime, couponId, false);
        }else {
            // 延时发放，设置状态为未开始发放
            result.setStatus(CouponStatus.UN_ISSUE);
            // 设置发放时间与结束时间
            result.setIssueBeginTime(beginTime);
            result.setIssueEndTime(endTime);
            // 定时任务
            task(now, beginTime, couponId, true);
        }
        updateById(result);

        if (isBegin) {
            // 添加缓存
            coupon.setIssueBeginTime(result.getIssueBeginTime());
            coupon.setIssueEndTime(result.getIssueEndTime());
            cacheCouponInfo(coupon);
        }
        // 判断是否需要发放兑换码
        if (coupon.getStatus() == CouponStatus.DRAFT || coupon.getObtainWay() == ObtainType.ISSUE) {
            coupon.setIssueEndTime(result.getIssueEndTime());
            exchangeCodeService.asyncGenerateCode(coupon);
        }
    }

    private void task(LocalDateTime now, LocalDateTime time, Long couponId, Boolean isBegin) {
        // 计算延时时间
        Duration delay = Duration.between(now, time);
        // 用MQ发送延时消息
        mqHelper.sendDelayMessage(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                ChangeStatusMessage.of(couponId, isBegin),
                delay);
    }


    private void cacheCouponInfo(Coupon coupon) {
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));

        redisTemplate.opsForHash().putAll(PromotionConstant.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
    }

    @Override
    public List<CouponVO> queryIssueCoupons() {
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        List<Long> cIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        List<UserCoupon> uCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, cIds)
                .list();
        // 统计用户已领取的优惠券数量
        Map<Long, Long> issuedMap = uCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting())); // 对couponId做分组，然后计算每组的个数（即已领取数量）
        // 统计用户已领取且未使用的优惠券数量
        Map<Long, Long> unusedMap = uCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 封装vo返回
        List<CouponVO> voList = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            // 设置是否可以领取参数：优惠券已发放数量小于总数量，且用户领取数量不能超过最多被允许领取的值
            vo.setAvailable(c.getIssueNum() < c.getTotalNum() && issuedMap.getOrDefault(c.getId(),0L) < c.getMaxDiscountAmount());

            // 设置是否可以被使用参数：当前用户已领取，且优惠券的未使用数量大于0
            vo.setReceived(unusedMap.getOrDefault(c.getId(),0L) > 0);
            voList.add(vo);
        }
        return voList;
    }

    @Override
    @Transactional
    public void pauseIssue(Long id) {
        // 查询旧的优惠券
        Coupon coupon = getById(id);
        if (BeanUtils.isEmpty(coupon)) {
            throw new BadRequestException("优惠券不存在!");
        }
        if (coupon.getStatus() != ISSUING && coupon.getStatus() != UN_ISSUE) {
            return;
        }

        // 更新状态
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .update();

        if (!success) {
            log.error("重复暂停优惠券,id:{}",id);
        }

        // 删除缓存
        redisTemplate.delete(PromotionConstant.COUPON_CACHE_KEY_PREFIX + id);
    }
}
