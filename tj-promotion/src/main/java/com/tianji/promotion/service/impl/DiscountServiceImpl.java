package com.tianji.promotion.service.impl;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper couponMapper;
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        // 1.查询我的可用数据库
        List<Coupon> coupons = couponMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 初步筛选
        // 求订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        // 2.筛选出符合订单总价的优惠券
        List<Coupon> availableCoupons = coupons.stream()
                .filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount, c))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        // 3.进一步筛选，筛选出可用的优惠券
        // 定义一个map集合，存放每个优惠券的可用课程
        Map<Coupon,List<OrderCourseDTO>> map = findAvailableCoupons(availableCoupons,orderCourses);
        // 排列组合
        availableCoupons = new ArrayList<>(map.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 添加单券方案（用户只想使用一张优惠券，其他优惠券不使用）
        for (Coupon coupon : availableCoupons) {
            solutions.add(List.of(coupon));
        }

        // 4.计算每个方案的优惠金额和订单总价(并行计算)
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        // 定义闭锁
        CountDownLatch latch = new CountDownLatch(availableCoupons.size());
        for (List<Coupon> solution : solutions) {
            // 异步计算
            CompletableFuture
                    .supplyAsync(() -> caculateSolutionDiscount(solution,map, orderCourses), discountSolutionExecutor)
                    .thenAccept(result -> {
                        list.add(result);
                        latch.countDown();
                    });
        }
        // 等待所有任务完成
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("计算优惠券方案优惠金额失败{}",e.getMessage());
        }
        // 筛选出最优解
        return findBestSolution(list);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        // 定义两个map，一个存储优惠金额最大的方案，一个存储使用优惠券最少的方案
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        for (CouponDiscountDTO solution : list) {
            // 对id字段进行排序
            String sortedKey = solution.getIds().stream()
                    .sorted(Long::compare).map(String::valueOf).collect(Collectors.joining(","));
            CouponDiscountDTO best = moreDiscountMap.get(sortedKey);
            if (best == null || best.getDiscountAmount() < solution.getDiscountAmount()) {
                // 当前方案比之前的方案金额多，设置当前方案为最优方案
                moreDiscountMap.put(sortedKey, solution);
            }

            best = lessCouponMap.get(solution.getDiscountAmount());
            if ((best == null || best.getIds().size() > solution.getIds().size()) || solution.getIds().size() == 1) {
                // 如果是单券，直接加到方案里面
                // 当前方案比最佳方案使用的优惠券少,设置当前方案为最优方案
                lessCouponMap.put(solution.getIds().size(), solution);
            }
        }
        // 求并集
        Collection<CouponDiscountDTO> result = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());

        return result.stream()
                .sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    private CouponDiscountDTO caculateSolutionDiscount(List<Coupon> solution,Map<Coupon,List<OrderCourseDTO>>  map, List<OrderCourseDTO> courses) {
        CouponDiscountDTO discountDTO = new CouponDiscountDTO();
        // 初始化每个课程的折扣明细（已经优惠了多少元）
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        // 计算每个优惠券的优惠金额
        for (Coupon coupon : solution) {
            // 查看优惠券可用的课程
            List<OrderCourseDTO> orderCourseDTOS = map.get(coupon);
            // 统计总金额（可用课程的价格-已经优惠的价格=优惠金额）
            int totalAmount = orderCourseDTOS.stream()
                    .mapToInt(oc -> oc.getPrice() - detailMap.get(oc.getId())).sum();
            // 判断当前总金额下能否使用该优惠券
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)){
                // 不可用，跳过该优惠券
                continue;
            }
            // 计算优惠金额
            int discountMoney = discount.calculateDiscount(totalAmount, coupon);
            // 更改每个课程的折扣明细（按比例优惠）
            caculateDiscountDetails(orderCourseDTOS, detailMap, totalAmount, discountMoney);
            discountDTO.getIds().add(coupon.getId());
            discountDTO.setDiscountAmount(discountMoney + discountDTO.getDiscountAmount());  // 累加
            discountDTO.getRules().add(discount.getRule(coupon));
        }
        return discountDTO;
    }

    private static void caculateDiscountDetails(List<OrderCourseDTO> orderCourseDTOS, Map<Long, Integer> detailMap, int totalAmount, int discountMoney) {
        int times = 0, remainDiscount = discountMoney; // 计数器
        for (OrderCourseDTO oc : orderCourseDTOS) {
            times++;
            if (times == orderCourseDTOS.size()) {
                // 如果是最后一个课程
                detailMap.put(oc.getId(), remainDiscount + detailMap.get(oc.getId()));
            }else {
                // 计算每个课程的金额
                int ocCurrentPrice = oc.getPrice() - detailMap.get(oc.getId());
                int ocDiscount = (ocCurrentPrice / totalAmount) * discountMoney;
                detailMap.put(oc.getId(), ocDiscount + detailMap.get(oc.getId()));
                remainDiscount -= ocDiscount;
            }

        }
    }

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourses = orderCourses;
            if (coupon.getSpecific()) {
                // 如果是有限定范围的优惠券，先跟据id查询优惠券的可使用范围，再在订单中筛选出可用的课程
                List<CouponScope> scopes = scopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                Set<Long> bizIds = scopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                availableCourses = orderCourses.stream().filter(c -> bizIds.contains(c.getId())).collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)) {
                // 没有优惠券对应的可用课程
                continue;
            }
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            boolean canUse = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon);
            if (canUse) {
                map.put(coupon, availableCourses);
            }
        }
        return map;
    }
}
