package com.tianji.aigc.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;

public interface MyChatMemoryRepository extends ChatMemoryRepository {

    /**
     * 根据对话ID优化对话记录，删除最后的2条消息，因为这条消息是从路由智能体存储的，请求由后续的智能体处理
     * 为了确保历史消息的完整性，所以需要将中间转发的消息清理掉
     *
     * @param conversationId 对话的唯一标识符
     */
    void optimization(String conversationId);

}
