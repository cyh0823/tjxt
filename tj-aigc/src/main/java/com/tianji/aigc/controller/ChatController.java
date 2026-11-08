package com.tianji.aigc.controller;

import com.tianji.aigc.domain.dto.ChatDTO;
import com.tianji.aigc.domain.vo.ChatEventVO;
import com.tianji.aigc.domain.vo.TemplateVO;
import com.tianji.aigc.service.IChatService;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final IChatService chatService;
    private static final TemplateVO TEMPLATE_VO = new TemplateVO();

    @NoWrapper
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        return chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

    @PostMapping
    public void stop(@RequestParam String sessionId){
        chatService.stop(sessionId);
    }

    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return chatService.chatText(question);
    }

    @GetMapping("/templates")
    public TemplateVO template() {
        return TEMPLATE_VO;
    }
}
