package com.tianji.api.client.ac;

import com.tianji.api.dto.ac.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "agentCenter", value = "agent-center-gateway", path = "/acs")
public interface AgentCenterClient {

    String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 认证接口（生成token）
     *
     * @param authRequestDTO 请求参数，包含app_key和app_secret
     * @return 认证结果，包含token
     */
    @PostMapping("/auth/token")
    AuthDTO auth(@RequestBody AuthRequestDTO authRequestDTO);

    /**
     * 创建会话
     *
     * @param num     会话数量
     * @param agentId 代理id
     * @param userId  用户id
     * @param token   认证token
     * @return 会话结果
     */
    @PostMapping("/session")
    SessionDTO createSession(@RequestParam("n") int num,
                             @RequestParam("agent_id") Long agentId,
                             @RequestParam("user_id") Long userId,
                             @RequestHeader(AUTHORIZATION_HEADER) String token);

    /**
     * 停止生成
     */
    @PostMapping("/chat/stop")
    Map<String, Object> stop(@RequestParam("agent_id") Long agentId,
                             @RequestParam("session_id") String sessionId,
                             @RequestHeader(AUTHORIZATION_HEADER) String token);

    @GetMapping("/session/hot")
    List<SessionDTO.Example> hotExamples(@RequestParam("n") int num,
                                         @RequestParam("agent_id") Long agentId,
                                         @RequestHeader(AUTHORIZATION_HEADER) String token);

    @GetMapping("/session/history")
    Map<String, List<ChatSessionDTO>> queryHistorySession(@RequestParam("agent_id") Long agentId,
                                                          @RequestParam("user_id") Long userId,
                                                          @RequestHeader(AUTHORIZATION_HEADER) String token);

    @DeleteMapping("/session/history")
    Map<String, Object> deleteHistorySession(@RequestParam("agent_id") Long agentId,
                                             @RequestParam("user_id") Long userId,
                                             @RequestParam("session_id") String sessionId,
                                             @RequestHeader(AUTHORIZATION_HEADER) String token);

    @PutMapping("/session/history")
    Map<String, Object> updateHistorySession(@RequestParam("agent_id") Long agentId,
                                             @RequestParam("user_id") Long userId,
                                             @RequestParam("session_id") String sessionId,
                                             @RequestParam("title") String title,
                                             @RequestHeader(AUTHORIZATION_HEADER) String token);

    @GetMapping("/session/{agentId}/{userId}/{sessionId}")
    List<MessageDTO> queryBySessionId(@PathVariable("agentId") Long agentId,
                                      @PathVariable("userId") Long userId,
                                      @PathVariable("sessionId") String sessionId,
                                      @RequestHeader(AUTHORIZATION_HEADER) String token);


}
