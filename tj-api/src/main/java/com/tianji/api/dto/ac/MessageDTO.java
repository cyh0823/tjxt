package com.tianji.api.dto.ac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    /**
     * 消息类型，USER表示用户提问，ASSISTANT表示AI的回答
     */
    private String type;
    /**
     * 消息内容
     */
    private String content;

    /**
     * 附加参数
     */
    private Map<String, Object> params;

}
