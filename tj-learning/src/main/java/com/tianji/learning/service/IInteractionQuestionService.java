package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-22
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(@Valid QuestionFormDTO questionDTO);

    PageDTO<QuestionVO> queryPageQuestion(QuestionPageQuery pageQuery);

    QuestionVO queryQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryPageAdminQuestion(QuestionAdminPageQuery pageQuery);

    QuestionAdminVO queryAdminQuestionById(Long id);

    void updateQuestion(Long id, QuestionFormDTO question);

    void removeQuestionById(Long id);

    void updateHidden(Long id, Boolean hidden);
}
