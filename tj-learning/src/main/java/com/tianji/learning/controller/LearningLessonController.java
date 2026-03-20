package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-19
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
@Api(tags = "用户已购课程接口")
public class LearningLessonController {

    private final ILearningLessonService iLearningLessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery){
        return iLearningLessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询我正在学习的课程")
    public LearningLessonVO queryMyNowLesson(){
        return iLearningLessonService.queryMyNowLesson();
    }

    @DeleteMapping("/{courseId}")
    @ApiOperation("根据课程ID删除课程")
    public void deleteByCourseId(@PathVariable("courseId") Long courseId){
        iLearningLessonService.removeByCourseId(courseId);
    }

    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return iLearningLessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}")
    public LearningLessonVO queryByCourseId(@PathVariable("courseId") Long courseId){
        return iLearningLessonService.queryByCourseId(courseId);
    }

    @GetMapping("/lessons/{courseId}/count")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return iLearningLessonService.countLearningLessonByCourse(courseId);
    }

}
