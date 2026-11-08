package com.tianji.aigc.tools;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import com.tianji.aigc.utils.RedisHashUtil;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.trade.OrderConfirmVO;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.tianji.aigc.constant.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final TradeClient tradeClient;
    private final RedisHashUtil redisHashUtil;

    @Tool(description = Constant.Tools.PRE_PLACE_ORDER)
    public PrePlaceOrder prePlaceOrder(@ToolParam(description = Constant.ToolParams.COURSE_IDS)  List<Number> courseIds, ToolContext toolContext){
        UserContext.setUser(Convert.toLong(toolContext.getContext().get(Constant.USER_ID)));
        OrderConfirmVO orderConfirmVO = tradeClient.prePlaceOrder(CollStreamUtil.toList(courseIds, Number::longValue));
        return Optional.ofNullable(orderConfirmVO)
                .map(PrePlaceOrder::of)
                .map(prePlaceOrder -> {
                    String field = Constant.Prefix.ORDER_PREFIX + prePlaceOrder.getOrderId();
                    String value = JSONUtil.toJsonStr(prePlaceOrder);
                    String key = StringUtils.toString(toolContext.getContext().get(Constant.REQUEST_ID));
                    redisHashUtil.hset(key, field, value, 30, TimeUnit.MINUTES);
                    return prePlaceOrder;
                })
                .orElse(null);
    }
}
