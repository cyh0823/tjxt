package com.tianji.aigc.tools;

import cn.hutool.json.JSONUtil;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.tianji.aigc.constant.Constant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CourseTool {

    private final CourseClient courseClient;
    private final RedisHashUtil redisHashUtil;

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryByCourseId(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId, ToolContext toolContext) {
        String requestId = toolContext.getContext().get(Constant.REQUEST_ID).toString();
        CourseBaseInfoDTO courseBaseInfoDTO = courseClient.baseInfo(courseId, true);
        if (BeanUtils.isEmpty(courseBaseInfoDTO)) {
            return null;
        }
        CourseInfo courseInfo = CourseInfo.of(courseBaseInfoDTO);

        redisHashUtil.hset(requestId, Constant.Prefix.COURSE_PREFIX + courseId,
                JSONUtil.toJsonStr(courseInfo),
                30, TimeUnit.MINUTES);

        return courseInfo;
    }
}
