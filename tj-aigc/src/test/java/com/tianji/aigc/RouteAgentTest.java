package com.tianji.aigc;

import cn.hutool.core.lang.Assert;
import com.tianji.aigc.agent.RouteAgent;
import com.tianji.aigc.enums.AgentTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RouteAgentTest {

    @Autowired
    private RouteAgent routeAgent;

    @Test
    public void testRouteAgent(){
        Assert.equals(routeAgent.process("我18岁，本科，想学java，有哪些课程", "1"), AgentTypeEnum.BUY.getAgentName());
    }
}
