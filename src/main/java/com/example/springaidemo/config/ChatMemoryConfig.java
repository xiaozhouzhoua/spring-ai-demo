package com.example.springaidemo.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 文件存储，保存到 chat 目录
        return new FileChatMemory("chat");
    }
}
