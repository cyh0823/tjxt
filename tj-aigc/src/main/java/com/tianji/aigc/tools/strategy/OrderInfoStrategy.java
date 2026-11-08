package com.tianji.aigc.tools.strategy;


import cn.hutool.json.JSONUtil;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
public class OrderInfoStrategy implements ToolResultStrategy{
    @Override
    public String prefix() {
        return Constant.Prefix.ORDER_PREFIX;
    }

    @Override
    public ChatEventVO buildEventVO(String json) {
        return ChatEventVO.builder()
                .eventData(JSONUtil.toBean(json, PrePlaceOrder.class))
                .eventType(ChatEventTypeEnum.PARAM.getValue())
                .build();
    }
}
