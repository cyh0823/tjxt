package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
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
}
