package com.tianji.api.dto.ac;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthDTO {

    private String token; // 令牌
    @JsonProperty("expire_hours")
    private Integer expireHours; // 过期时间，单位：小时
    @JsonProperty("app_id")
    private Long appId; // 应用ID
}
