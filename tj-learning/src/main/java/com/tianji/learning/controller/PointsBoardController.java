package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-24
 */
@RestController
@RequestMapping("/boards")
@Api(tags = "积分排行榜")
@RequiredArgsConstructor
public class PointsBoardController {

    private final IPointsBoardService pointsBoardService;

    @GetMapping
    @ApiOperation("根据赛季id查询积分排行榜")
    public PointsBoardVO getPointsBoardBySeason(PointsBoardQuery pointsBoardQuery) {
        return pointsBoardService.getPointsBoardBySeason(pointsBoardQuery);
    }
}
