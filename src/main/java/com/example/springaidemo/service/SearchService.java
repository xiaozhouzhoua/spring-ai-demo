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
                2. 首先显示搜索摘要信息，使用 ```search-summary 代码块格式
                3. 然后显示详细的搜索过程，使用 ```search-process 代码块格式
                4. 搜索结果按分类组织：技术文档、新闻资讯、教程指南、问答社区、官方网站、其他资源
                5. 每个分类下显示相关结果，包含标题、链接和摘要
                6. 在回答中整合搜索到的信息，提供准确和有用的回答
                7. 最后列出主要信息来源
                
                搜索摘要格式示例：
                ```search-summary
                状态: 搜索中
                查询: [用户查询]
                结果数量: [数量]
                分类: [分类列表]
                ```
                
                搜索过程格式示例：
                ```search-process
                🔍 联网搜索过程
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                查询词: [查询词]
                搜索时间: [时间]
                结果总数: [数量]
                
                📂 [分类名称] ([数量]条)
                ─────────────────────────────────────────
                1. [标题]
                   🔗 [链接]
                   📄 [摘要]
                ```
                """.formatted(today))
            .user(question)
            .functions("exaSearch")
            .advisors(new SimpleLoggerAdvisor())
            .call()
            .entity(SearchResult.class);
    }
}
