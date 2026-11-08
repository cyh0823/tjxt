package com.tianji.aigc.generator;

import com.tianji.aigc.config.SystemPromptConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TextGenerator {

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;

    public String generate(String question) {
        return chatClient
                .prompt()
                .system(systemPromptConfig.getTextSystemMessage().get())
                .user(question)
                .call()
                .content();
    }
}
