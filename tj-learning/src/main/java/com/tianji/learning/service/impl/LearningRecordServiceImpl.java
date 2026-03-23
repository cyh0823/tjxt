package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.DelayTask;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-20
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService iLearningLessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler handler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1.查询当前用户
        Long userId = UserContext.getUser();
        // 2.根据当前用户查询课表
        LearningLesson lesson = iLearningLessonService.queryByCourseIdAndUserId(courseId,userId);
        if (lesson == null){
            return null;
        }
        // 3.查询学习记录
        List<LearningRecord> records = lambdaQuery().eq(LearningRecord::getLessonId, lesson.getId()).list();

        // 4.封装数据
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return learningLessonDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addLearningRecord(LearningRecordFormDTO dto) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.处理学习记录
        boolean finished = false; // 是否第一次学完（判断要不要新增课表中的小节数）
        if(dto.getSectionType() == SectionType.EXAM){
            finished = handleExamRecord(userId,dto);
        }else {
            finished = handleVideoRecord(userId,dto);
        }
        if (!finished) {
            // 如果不是第一次学完，无需更新课表中的学习记录
            return;
        }
        // 3.处理课程数据
        handleLearningLessonChange(dto);
    }


    private void handleLearningLessonChange(LearningRecordFormDTO dto) {
        // 查询课表
        LearningLesson learningLesson = iLearningLessonService.getById(dto.getLessonId());
        if(learningLesson == null){
            throw new BizIllegalException("课程不存在，无法更新数据");
        }
        boolean allLearned = false;
        // 如果有新学完的小节，则需要查询课程数据
        CourseFullInfoDTO cInfo =
                courseClient.getCourseInfoById(learningLesson.getCourseId(), false, false);
        if(cInfo == null){
            throw new BizIllegalException("课程不存在，无法更新数据");
        }
        // 判断课程是不是全部学完
        allLearned = learningLesson.getLearnedSections() + 1 >= cInfo.getSectionNum();
        // 更新课表
        iLearningLessonService.lambdaUpdate()
                .set(learningLesson.getLearnedSections() == 0,LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allLearned,LearningLesson::getStatus, LessonStatus.FINISHED)
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId,learningLesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        // 1.查询旧的学习记录
        LearningRecord old = queryOldRecord(dto.getLessonId(),dto.getSectionId());
        if (old == null){
            // 2.1 不存在，需要添加学习记录
            LearningRecord learningRecord = BeanUtils.copyProperties(dto, LearningRecord.class);
            learningRecord.setUserId(userId);
            boolean save = save(learningRecord);
            if(!save){
                throw new DbException("保存学习记录失败！");
            }
            return false;
        }
        // 判断是否是第一次完成
        boolean finished = !old.getFinished() && (dto.getMoment() * 2 >= dto.getDuration());
        if (!finished) {
            // 如果不是第一次学完，需要添加到缓存，创建延时任务
            LearningRecord record = new LearningRecord();
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            handler.addLearningRecordTask(record);
            return false;
        }
        // 更新
        boolean updateFinished = lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        handler.removeRecordCache(dto.getLessonId(), dto.getSectionId());
        if(!updateFinished){
            throw new DbException("更新学习记录失败！");
        }
        return true;
    }

    private LearningRecord queryOldRecord(@NotNull(message = "课表id不能为空") Long lessonId, @NotNull(message = "节的id不能为空") Long sectionId) {
        // 查缓存
        LearningRecord record = handler.readRecordCache(lessonId, sectionId);
        if (record == null){
            // 为空，则查询数据库，并写入缓存
            record = lambdaQuery()
                    .eq(LearningRecord::getLessonId, lessonId)
                    .eq(LearningRecord::getLessonId, sectionId)
                    .one();
            handler.writeRecordCache(record);
            return record;
        }else {
            // 非空，直接返回缓存内容就好
            return record;
        }
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        // 1.将DTO封装为VO
        LearningRecord learningRecord = BeanUtils.copyProperties(dto, LearningRecord.class);
        learningRecord.setUserId(userId);
        learningRecord.setFinished(true);
        learningRecord.setFinishTime(dto.getCommitTime());
        boolean save = save(learningRecord);
        if(!save){
            throw new DbException("保存考试记录失败！");
        }
        return true;
    }
}
