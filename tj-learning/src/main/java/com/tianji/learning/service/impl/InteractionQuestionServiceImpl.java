package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-22
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;
    private final UserClient userClient;
    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        Long userId = UserContext.getUser();
        InteractionQuestion interactionQuestion = BeanUtils.copyProperties(questionDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        save(interactionQuestion);
    }

    @Override
    public PageDTO<QuestionVO> queryPageQuestion(QuestionPageQuery pageQuery) {
        Long courseId = pageQuery.getCourseId();
        Long sectionId = pageQuery.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("查询的小节数和课程不能同时为空");
        }
        Page<InteractionQuestion> page = lambdaQuery()
                .select(info -> !info.getProperty().equals("description"))
                .eq(pageQuery.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 根据Id查询提问者和最近一次的回答者
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        for (InteractionQuestion q : records) {
            if (!q.getAnonymity()) {
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 根据Id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if (CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                // 在查询回复者时，可以将非匿名的回复者id也加入到查询列表中，方便后续查询用户信息
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }
        // 根据id查询用户信息
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 封装为VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            // 如果用户是匿名的，就不查询用户信息了
            if (!r.getAnonymity()) {
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserName(userDTO.getName());
                }
            }
            InteractionReply interactionReply = replyMap.get(r.getLatestAnswerId());
            if (interactionReply != null) {
                vo.setLatestReplyContent(interactionReply.getContent());
                // 如果回答者是匿名的，同样不用查询回答者的姓名（前面在将回答者添加到UserMap中已经判断是否匿名了）
                if (!interactionReply.getAnonymity()) {
                    vo.setLatestReplyUser(userMap.get(interactionReply.getUserId()).getName());
                }
            }
            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        if(question == null || question.getHidden()) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        if(!question.getAnonymity()) {
            userDTO = userClient.queryUserById(question.getUserId());
        }
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryPageAdminQuestion(QuestionAdminPageQuery pageQuery) {
        List<Long> courseIds = null;
        if (!StringUtils.isBlank(pageQuery.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(pageQuery.getCourseName());
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L,0L);
            }
        }
        Integer status = pageQuery.getStatus();
        LocalDateTime begin = pageQuery.getBeginTime();
        LocalDateTime end = pageQuery.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        for (InteractionQuestion record : records) {
            userIds.add(record.getUserId());
            cIds.add(record.getSectionId());
            cataIds.add(record.getSectionId());
            cataIds.add(record.getChapterId());
        }

        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(userDTOS.size());
        if(CollUtils.isNotEmpty(userDTOS)){
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> courseMap = new HashMap<>(simpleInfoList.size());
        if(CollUtils.isNotEmpty(simpleInfoList)) {
            courseMap = simpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        List<CataSimpleInfoDTO> cInfos = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(cInfos.size());
        if(CollUtils.isNotEmpty(cInfos)) {
            cataMap = cInfos.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion record : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            voList.add(vo);
            UserDTO user = userMap.get(record.getUserId());
            if (BeanUtils.isNotEmpty(user)) {
                vo.setUserName(userMap.get(record.getUserId()).getName());
                vo.setUserIcon(userMap.get(record.getUserId()).getIcon());
            }

            CourseSimpleInfoDTO cInfo = courseMap.get(record.getCourseId());
            if (BeanUtils.isNotEmpty(cInfo)) {
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }

            vo.setChapterName(cataMap.getOrDefault(record.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(record.getSectionId(), ""));

        }

        return PageDTO.of(page,voList);
    }

    @Override
    public QuestionAdminVO queryAdminQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        if (BeanUtils.isEmpty(question)) {
            return null;
        }
        UserDTO userDTO = userClient.queryUserById(question.getUserId());
        CourseFullInfoDTO courseDTO = courseClient.getCourseInfoById(question.getCourseId(), true, true);
        CataSimpleInfoDTO chapterInfo = catalogueClient.batchQueryCatalogue(CollUtils.toList(question.getChapterId())).get(0);
        CataSimpleInfoDTO sectionInfo = catalogueClient.batchQueryCatalogue(CollUtils.toList(question.getSectionId())).get(0);
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        if (vo.getStatus() == 0) {
            vo.setStatus(1);
        }
        if (BeanUtils.isNotEmpty(userDTO)) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        if (BeanUtils.isNotEmpty(courseDTO)) {
            vo.setCourseName(courseDTO.getName());
            // 将老师集合处理成字符串，中间使用、隔开
            String teacherName = userClient
                    .queryUserByIds(courseDTO.getTeacherIds())
                    .stream().map(UserDTO::getName).collect(Collectors.joining("、"));
            vo.setTeacherName(teacherName);
            vo.setCategoryName(categoryCache.getCategoryNames(courseDTO.getCategoryIds()));
        }
        if (BeanUtils.isNotEmpty(chapterInfo)) {
            vo.setChapterName(chapterInfo.getName());
        }
        if (BeanUtils.isNotEmpty(sectionInfo)) {
            vo.setSectionName(sectionInfo.getName());
        }
        return vo;
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO question) {
        if (id == null || question == null) {
            throw new IllegalArgumentException("问题ID或更新参数不能为空");
        }
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(question, InteractionQuestion.class);
        interactionQuestion.setId(id);
        boolean b = updateById(interactionQuestion);
        if (!b) {
            throw new IllegalArgumentException("问题不存在，更新失败");
        }
    }

    @Override
    public void removeQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        if (BeanUtils.isEmpty(question)) {
            throw new BadRequestException("问题不存在");
        }
        Long userId = UserContext.getUser();
        if (!Objects.equals(userId,question.getUserId())) {
            throw new IllegalArgumentException("您没有权限删除该问题");
        }
        // TODO 删除问题下的所有回答和评论（完成回答和评论相关接口后）
    }

    @Override
    public void updateHidden(Long id, Boolean hidden) {
        boolean update = lambdaUpdate()
                .set(InteractionQuestion::getHidden, hidden)
                .eq(InteractionQuestion::getId, id)
                .update();
        if (!update) {
            throw new BadRequestException("更新失败");
        }
    }
}
