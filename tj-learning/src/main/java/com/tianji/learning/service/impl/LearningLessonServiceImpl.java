package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-19
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final LearningRecordMapper learningRecordMapper;
    private final CatalogueClient catalogueClient;
    private final IPointsRecordService pointsRecordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUserLesson(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)){
            return;
        }
        // 2.循环遍历，处理LearningLesson
        List<LearningLesson> lessonList = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            Integer durationMonth = cInfo.getValidDuration();
            if (durationMonth != null || durationMonth < 0){
                LocalDateTime startDateTime = LocalDateTime.now();
                lesson.setCreateTime(startDateTime);
                lesson.setExpireTime(startDateTime.plusMonths(durationMonth));
            }
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            lessonList.add(lesson);
        }
        // 3.批量添加
        saveBatch(lessonList);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        // 获取当前用户id
        Long userId = UserContext.getUser();
        // 分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        // 解析分页查询的内容
        List<LearningLesson> lessonList = page.getRecords();
        if(CollUtils.isEmpty(lessonList)){
            return PageDTO.empty(page);
        }
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(lessonList);
        List<LearningLessonVO> voList = new ArrayList<>(lessonList.size());
        for (LearningLesson lesson : lessonList) {
            LearningLessonVO lessonVO = BeanUtils.copyProperties(lesson, LearningLessonVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(lesson.getId());
            lessonVO.setCourseName(cInfo.getName());
            lessonVO.setCourseCoverUrl(cInfo.getCoverUrl());
            lessonVO.setSections(cInfo.getSectionNum());
            voList.add(lessonVO);
        }
        return PageDTO.of(page,voList);
    }

    @Override
    public LearningLessonVO queryMyNowLesson() {
        Long userId = UserContext.getUser();
        // 查询最新课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        // 拷贝部分属性(这里只有courseId，没有courseName，需要查询)
        LearningLessonVO nowLesson = BeanUtils.copyProperties(lesson, LearningLessonVO.class);
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        nowLesson.setCourseName(courseInfo.getName());
        nowLesson.setSections(courseInfo.getSectionNum());
        nowLesson.setCourseCoverUrl(courseInfo.getCoverUrl());
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        nowLesson.setCourseAmount(courseAmount);
        List<CataSimpleInfoDTO> latestLessons = catalogueClient
                .batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if(!CollUtils.isEmpty(latestLessons)){
            CataSimpleInfoDTO latestLesson = latestLessons.get(0);
            nowLesson.setLatestSectionName(latestLesson.getName());
            nowLesson.setLatestSectionIndex(latestLesson.getCIndex());
        }
        return nowLesson;
    }

    @Override
    public void removeByCourseId(Long courseId) {
        remove(lambdaQuery().eq(LearningLesson::getCourseId,courseId).eq(LearningLesson::getUserId,UserContext.getUser()));
    }

    @Override
    public Long isLessonValid(Long courseId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null || lesson.getStatus().equals(LessonStatus.EXPIRED)){
            // 过期或查询结果为空
            return null;
        }
        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryByCourseId(Long courseId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        return BeanUtils.copyProperties(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery().eq(LearningLesson::getCourseId,courseId).count();
    }

    @Override
    public LearningLesson queryByCourseIdAndUserId(Long courseId, Long userId) {
        return lambdaQuery()
                .eq(LearningLesson::getCourseId,courseId)
                .eq(LearningLesson::getUserId,userId)
                .one();
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        // 获取当前用户
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        AssertUtils.isNotNull(lesson,"课程信息不存在！");
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    @Override
    public LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        Long userId = UserContext.getUser();
        // 查询总的统计数
        // 获取本周已学习小节数
        LocalDateTime begin = DateUtils.getWeekBeginTime(LocalDate.now());
        LocalDateTime end = DateUtils.getWeekEndTime(LocalDate.now());
        Integer count = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinished, begin)
                .lt(LearningRecord::getFinished, end)
        );
        result.setWeekFinished(count);
        // 获取本周总共小节数
        getBaseMapper().queryTotalPlans(userId);
        // 查询本周学习积分
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        List<PointsRecord> pointsRecords = pointsRecordService.getPointsRecords(userId, weekBeginTime, weekEndTime);
        int sumPoints = pointsRecords.stream().mapToInt(PointsRecord::getPoints).sum();
        result.setWeekPoints(sumPoints);

        // 查询分页记录
        // 分页查询课表信息以及学习计划信息
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return result.pageInfo(page.getTotal(),page.getPages(),null);
        }

        // 查询课表对应的课程信息：课程名字等
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);

        // 统计每个课程本周已学习的小节数
        List<IdAndNumDTO> list = learningRecordMapper.countLearnedSections(userId,begin,end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);

        // 封装VO
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            LearningPlanVO vo = BeanUtils.copyBean(record, LearningPlanVO.class);
            // 填充课程基本信息
            CourseSimpleInfoDTO cInfo = cMap.get(record.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            // 每周已学习小节数
            vo.setLearnedSections(countMap.getOrDefault(record.getCourseId(), 0));
            voList.add(vo);
        }
        return result.pageInfo(page.getTotal(),page.getPages(),voList);
    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> lessonList) {
        Set<Long> cIds = lessonList.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(courseList)){
            throw new BadRequestException("课程信息不存在！");
        }
        Map<Long, CourseSimpleInfoDTO> cMap =
                courseList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }
}
