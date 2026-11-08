package com.tianji.aigc.service;

import com.tianji.aigc.domain.po.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.aigc.domain.vo.ChatSessionVO;
import com.tianji.aigc.domain.vo.MessageVO;
import com.tianji.aigc.domain.vo.SessionVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 对话session 服务类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-09-07
 */
public interface IChatSessionService extends IService<ChatSession> {

    SessionVO createSession(Integer num);

    List<SessionVO.Example> getHotSession(Integer num);

    List<MessageVO> queryBySessionId(String sessionId);

    void update(String sessionId, String title, Long userId);

    Map<String, List<ChatSessionVO>> queryHistorySession();

    void deleteById(String sessionId);

    void updateTitle(String sessionId, String title);
}
