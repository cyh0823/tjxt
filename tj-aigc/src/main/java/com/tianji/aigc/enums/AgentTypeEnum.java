package com.tianji.aigc.enums;

import com.tianji.common.utils.EnumUtils;
import lombok.Getter;

@Getter
public enum AgentTypeEnum {
    ROUTE("ROUTE", "路由智能体"),
    RECOMMEND("RECOMMEND", "课程推荐智能体"),
    CONSULT("CONSULT", "课程咨询智能体"),
    BUY("BUY", "课程购买智能体"),
    KNOWLEDGE("ROUTE", "知识讲解智能体");

    private final String agentName;
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.name();
    }

    public static AgentTypeEnum of(String agentName) {
        return EnumUtils.fromString(AgentTypeEnum.class, agentName);
    }
}
