package com.tianji.aigc.controller;


import com.tianji.aigc.domain.vo.ChatSessionVO;
import com.tianji.aigc.domain.vo.MessageVO;
import com.tianji.aigc.domain.vo.SessionVO;
import com.tianji.aigc.service.IChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 对话session 前端控制器
 * </p>
 *
 * @author cyhhyc
 * @since 2026-09-07
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class ChatSessionController {

    private final IChatSessionService chatSessionService;

    @PostMapping
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num){
        return chatSessionService.createSession(num);
    }

    @GetMapping("/hot")
    public List<SessionVO.Example> getHotSession(@RequestParam(value = "n", defaultValue = "3") Integer num){
        return chatSessionService.getHotSession(num);
    }

    @GetMapping("/{sessionId}")
    public List<MessageVO> queryBySessionId(@PathVariable("sessionId") String sessionId){
        return chatSessionService.queryBySessionId(sessionId);
    }

    @GetMapping("/history")
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        return chatSessionService.queryHistorySession();
    }

    @DeleteMapping("/history")
    public void deleteById(@RequestParam("sessionId") String sessionId) {
        chatSessionService.deleteById(sessionId);
    }

    @PutMapping("/history")
    public void updateTitle(@RequestParam("sessionId") String sessionId,
                            @RequestParam("title") String title) {
        chatSessionService.updateTitle(sessionId, title);
    }
}
