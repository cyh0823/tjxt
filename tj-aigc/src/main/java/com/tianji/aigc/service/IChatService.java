package com.tianji.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.aigc.domain.po.ChatSession;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

public interface IChatService extends IService<ChatSession> {

    static String getConversationId(String sessionId) {
        return UserContext.getUser() + "_" + sessionId;
    }

    Flux<ChatEventVO> chat(String question, String sessionId);

    void stop(String sessionId);

    String chatText(String question);
}
