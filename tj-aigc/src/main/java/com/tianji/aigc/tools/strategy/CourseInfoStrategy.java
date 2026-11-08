package com.tianji.aigc.tools.strategy;

import cn.hutool.json.JSONUtil;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.aigc.utils.RedisHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CourseInfoStrategy implements ToolResultStrategy{

    @Override
    public String prefix() {
        return Constant.Prefix.COURSE_PREFIX;
    }

    @Override
    public ChatEventVO buildEventVO(String json) {
        return ChatEventVO.builder()
                .eventData(JSONUtil.toBean(json, CourseInfo.class))
                .eventType(ChatEventTypeEnum.PARAM.getValue())
                .build();
    }
}
