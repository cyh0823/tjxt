package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-20
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    LearningLessonDTO queryLearningRecordByCourse(Long courseId);

    void addLearningRecord(LearningRecordFormDTO dto);
}
