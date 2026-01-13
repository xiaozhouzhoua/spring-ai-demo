package com.example.springaidemo.service;

import com.example.springaidemo.model.SearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class SearchService {

    private final ChatClient chatClient;

    public SearchService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public SearchResult search(String question) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        
        return chatClient.prompt()
            .system("""
                你是一个搜索助手。当前日期是：%s
                
                请遵循以下规则：
                1. 使用中文回答
                2. 每条结果单独一行，格式为：序号. 标题 - 简要描述
                3. 条目之间用换行分隔，保持清晰易读
                4. 最后列出信息来源
                """.formatted(today))
            .user(question)
            .functions("exaSearch")
            .advisors(new SimpleLoggerAdvisor())
            .call()
            .entity(SearchResult.class);
    }
}
