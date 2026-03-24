package com.tianji.remark.controller;


import com.tianji.api.dto.remark.LikeTimesDto;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-23
 */
@RestController
@RequestMapping("/likes")
@Api(tags = "点赞相关接口")
@RequiredArgsConstructor
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @PostMapping
    @ApiOperation("点赞或取消点赞")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO recordDTO){
        likedRecordService.addLikeRecord(recordDTO);
    }

    @GetMapping("/list")
    @ApiOperation("查询用户点赞的业务id列表")
    public Set<Long> isBizLiked(@RequestParam List<Long> bizIds){
        return likedRecordService.isBizLiked(bizIds);
    }

}
