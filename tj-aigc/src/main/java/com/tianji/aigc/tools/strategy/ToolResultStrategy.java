package com.tianji.aigc.tools.strategy;

import com.tianji.aigc.domain.vo.ChatEventVO;

// 采用策略模式：根据field的前缀为json生成对应的类，进而生成对应的ChatEventVO
public interface ToolResultStrategy {

    String prefix();

    default boolean support(String field){
        return field.startsWith(prefix());
    }

    ChatEventVO buildEventVO(String json);
}
