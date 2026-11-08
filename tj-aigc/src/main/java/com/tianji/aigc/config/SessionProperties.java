package com.tianji.aigc.config;

import com.tianji.aigc.domain.vo.SessionVO;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "tj.ai.session")
public class SessionProperties {
    private String title;
    private String describe;
    private List<SessionVO.Example> examples;
}
