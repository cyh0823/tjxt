package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
@RequestMapping("/admin/questions")
@Api(tags = "管理端提问相关接口")
@RequiredArgsConstructor
public class InteractionAdminQuestionController {

    private final IInteractionQuestionService interactionQuestionService;

    @GetMapping("/page")
    @ApiOperation(value = "分页查询提问问题")
    public PageDTO<QuestionAdminVO> queryPageQuestion(QuestionAdminPageQuery pageQuery){
        return interactionQuestionService.queryPageAdminQuestion(pageQuery);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "根据id查询提问问题")
    public QuestionAdminVO queryAdminQuestionById(@PathVariable Long id){
        return interactionQuestionService.queryAdminQuestionById(id);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("更新提问问题是否隐藏")
    public void updateHidden(@PathVariable Long id, @PathVariable Boolean hidden){
        interactionQuestionService.updateHidden(id, hidden);
    }

}
