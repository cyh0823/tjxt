package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-19
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLesson(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryMyNowLesson();

    void removeByCourseId(Long courseId);

    Long isLessonValid(Long courseId);

    LearningLessonVO queryByCourseId(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);
}
