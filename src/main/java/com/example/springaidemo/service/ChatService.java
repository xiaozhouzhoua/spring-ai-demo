package com.example.springaidemo.service;

import com.example.springaidemo.config.FileChatMemory;
import com.example.springaidemo.controller.ChatController.SessionDto;
import com.example.springaidemo.tool.ExaSearchTool.ExaSearchRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatClient chatClientWithoutMemory;  // 不带自动记忆的客户端
    private final ChatMemory chatMemory;
    private final Function<ExaSearchRequest, String> exaSearch;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
                       Function<ExaSearchRequest, String> exaSearch) {
        this.chatMemory = chatMemory;
        this.exaSearch = exaSearch;
        
        // 带自动记忆的客户端（普通聊天用）
        this.chatClient = chatClientBuilder
            .clone()  // 克隆一份，避免影响其他配置
            .defaultSystem("请使用中文回答所有问题。")
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(chatMemory),
                new SimpleLoggerAdvisor()
            )
            .build();
        
        // 不带自动记忆的客户端（搜索场景手动管理记忆）
        this.chatClientWithoutMemory = chatClientBuilder
            .clone()  // 克隆一份
            .defaultSystem("请使用中文回答所有问题。")
            .build();
    }

    public Flux<String> streamChat(String sessionId, String message) {
        return streamChat(sessionId, message, false);
    }

    public Flux<String> streamChat(String sessionId, String message, boolean enableSearch) {
        if (enableSearch) {
            // 执行搜索
            String searchResult = exaSearch.apply(new ExaSearchRequest(message));
            
            // 搜索结果用特殊标记包裹，前端可以识别并单独渲染
            String searchBlock = "<!--SEARCH_START-->" + searchResult + "<!--SEARCH_END-->\n\n";
            
            String userMessageWithContext = """
                你是一个知识渊博的助手。请基于以下搜索结果，用自然流畅的中文回答用户的问题。
                
                格式要求（非常重要）：
                - 使用 Markdown 格式输出
                - 段落之间必须空一行
                - 如果有多个要点，使用列表格式，每个要点单独一行
                - 不要把所有内容挤在一段里
                
                内容要求：
                - 用自己的语言组织答案，不要复制原文
                - 提炼关键信息，回答要有逻辑性
                - 不要显示URL链接
                
                搜索结果（JSON格式）：
                %s
                
                用户问题：%s
                """.formatted(searchResult, message);
            
            // 手动保存用户原始消息
            chatMemory.add(sessionId, List.of(new UserMessage(message)));
            
            // 收集完整的 AI 回复
            StringBuilder fullResponse = new StringBuilder(searchBlock);
            
            // 使用不带自动记忆的客户端
            return Flux.just(searchBlock)
                .concatWith(chatClientWithoutMemory.prompt()
                    .user(userMessageWithContext)
                    .stream()
                    .content()
                    .doOnNext(fullResponse::append))
                .doOnComplete(() -> {
                    // 流结束后保存完整的 AI 回复（包含搜索结果块）
                    chatMemory.add(sessionId, List.of(new AssistantMessage(fullResponse.toString())));
                });
        }
        
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
            .stream()
            .content();
    }

    public String chat(String sessionId, String message) {
        return this.chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
            .call()
            .content();
    }

    public List<Message> getHistory(String sessionId) {
        return chatMemory.get(sessionId, 100);
    }

    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }

    public List<SessionDto> getAllSessions() {
        if (chatMemory instanceof FileChatMemory fileChatMemory) {
            return fileChatMemory.getAllSessions().stream()
                .map(s -> new SessionDto(s.id(), s.title(), s.timestamp()))
                .toList();
        }
        return List.of();
    }

    public void updateSessionTitle(String sessionId, String title) {
        if (chatMemory instanceof FileChatMemory fileChatMemory) {
            fileChatMemory.updateTitle(sessionId, title);
        }
    }
}
