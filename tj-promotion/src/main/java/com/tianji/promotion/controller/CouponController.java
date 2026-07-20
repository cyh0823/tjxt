package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-27
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Api(tags = "优惠券相关接口")
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("保存优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.saveCoupon(couponFormDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券信息")
    public PageDTO<CouponPageVO> couponPage(CouponQuery query) {
        return couponService.couponPage(query);
    }

    @PutMapping("/{id}/issue")
    @ApiOperation("开始发放优惠券")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO couponIssueFormDTO) {
        couponService.beginIssue(couponIssueFormDTO);
    }

    @GetMapping("/list")
    @ApiOperation("查询发放中的优惠券列表")
    public List<CouponVO> queryIssueCoupons() {
        return couponService.queryIssueCoupons();
    }

    @PutMapping("/{id}/pause")
    @ApiOperation("暂停发放优惠券")
    public void pauseIssue(@ApiParam(value = "优惠券id") @PathVariable Long id) {
        couponService.pauseIssue(id);
    }
}
