package com.tianji.learning.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-20
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    @Select("select user_id AS id , count(1) AS num " +
            "from tj_learning.learning_record " +
            "where user_id = #{userId} " +
            "AND finished = 1 " +
            "AND finish_time > #{begin} AND finish_time < #{end} " +
            "group by lesson_id")
    List<IdAndNumDTO> countLearnedSections(Long userId, LocalDateTime begin, LocalDateTime end);
}
