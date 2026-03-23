package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-22
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "用户提问相关接口")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService interactionQuestionService;

    @PostMapping
    @ApiOperation("用户提问")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO){
        interactionQuestionService.saveQuestion(questionDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询用户提问")
    public PageDTO<QuestionVO> queryPageQuestion(QuestionPageQuery pageQuery){
        return interactionQuestionService.queryPageQuestion(pageQuery);
    }

    @GetMapping("/{id}}")
    @ApiOperation("根据用户提问id查询用户提问")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return interactionQuestionService.queryQuestionById(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("更新用户提问")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO question){
        interactionQuestionService.updateQuestion(id, question);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除用户提问")
    public void removeQuestionById(@PathVariable Long id){
        interactionQuestionService.removeQuestionById(id);
    }
}
