package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final CatalogueClient catalogueClient;

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
        Set<Long> cIds = lessonList.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courseList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(courseList)){
            throw new BadRequestException("课程信息不存在！");
        }
        Map<Long, CourseSimpleInfoDTO> cMap =
                courseList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
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
}
