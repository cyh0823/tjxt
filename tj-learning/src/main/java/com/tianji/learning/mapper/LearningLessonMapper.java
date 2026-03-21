package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-19
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    @Select("select sum(week_freq) from tj_learning.learning_lesson " +
            "where user_id = #{userId} and plan_status = 1 and status in (0,1)")
    void queryTotalPlans(@Param("userId") Long userId);
}
