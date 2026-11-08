package com.tianji.aigc.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {

    private String sessionId;

    private String title;

    private String description;

    private List<Example> examples;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Example{
        private String title;

        private String describe;
    }
}
