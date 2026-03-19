package com.tianji.learning.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-19
 */
@RestController
@RequestMapping("/lesson")
@RequiredArgsConstructor
@Api(tags = "用户已购课程接口")
public class LearningLessonController {

    private final ILearningLessonService iLearningLessonService;

    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery){
        return iLearningLessonService.queryMyLessons(pageQuery);
    }
}
