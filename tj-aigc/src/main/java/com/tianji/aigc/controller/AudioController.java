package com.tianji.aigc.controller;

import com.tianji.aigc.service.AudioService;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
@Slf4j
public class AudioController {
    private final AudioService audioService;

    // 文字转语音接口
    @NoWrapper
    @PostMapping("/tts-stream")
    public ResponseBodyEmitter ttsStream(@RequestBody String text) {
        return audioService.ttsStream(text);
    }

    // 语音转文字接口
    @PostMapping("/stt")
    public String stt(@RequestParam("audioFile") MultipartFile audioFile) {
        return audioService.stt(audioFile);
    }
}
