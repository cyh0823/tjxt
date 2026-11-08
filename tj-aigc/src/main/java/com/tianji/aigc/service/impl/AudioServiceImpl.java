package com.tianji.aigc.service.impl;

import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisOutput;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.ByteBuffer;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioServiceImpl implements AudioService {

    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;
    private final DashScopeAudioSpeechModel speechModel;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        log.info("开始生成语音{}", text);
        SpeechSynthesisPrompt speechSynthesisPrompt = new SpeechSynthesisPrompt(text);
        Flux<SpeechSynthesisResponse> stream = speechModel.stream(speechSynthesisPrompt);
        stream.subscribe(
                speechResponse -> {
                    try {
                        // 获取响应输出的数据，并发送到响应体中
                        ByteBuffer audio = speechResponse.getResult().getOutput().getAudio();
                        byte[] bytes = new byte[audio.remaining()];
                        audio.get(bytes);
                        emitter.send(bytes, MediaType.parseMediaType("audio/mpeg"));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }

    @Override
    public String stt(MultipartFile audioFile) {
        Resource audioFileResource = audioFile.getResource();
        AudioTranscriptionPrompt transcriptionPrompt = new AudioTranscriptionPrompt(audioFileResource);
        AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(transcriptionPrompt);
        String output = response.getResult().getOutput();
        // 翻译结果可能有繁体字，转化为简体字
        return ZhConverterUtil.toSimple(output);
    }
}
