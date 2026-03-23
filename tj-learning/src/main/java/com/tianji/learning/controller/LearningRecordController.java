package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-20
 */
@RestController
@RequestMapping("/learning-records")
@Api(tags = "学习记录相关接口")
@RequiredArgsConstructor
public class LearningRecordController {

    private final ILearningRecordService iLearningRecordService;

    @GetMapping("/learning-records/course/{courseId}")
    @ApiOperation("查询指定课程的学习记录")
    public LearningLessonDTO queryLearningRecordByCourse(
            @ApiParam(value = "课程Id", example = "2") @PathVariable("courseId") Long courseId){
            return iLearningRecordService.queryLearningRecordByCourse(courseId);
    }

    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody LearningRecordFormDTO dto){
        iLearningRecordService.addLearningRecord(dto);
    }

}
