package com.example.springaidemo.service;

import com.example.springaidemo.config.FileChatMemory;
import com.example.springaidemo.controller.ChatController.SessionDto;
import com.example.springaidemo.tool.ExaSearchTool.ExaSearchRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Function<ExaSearchRequest, String> exaSearch;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
                       Function<ExaSearchRequest, String> exaSearch) {
        this.chatMemory = chatMemory;
        this.exaSearch = exaSearch;
        this.chatClient = chatClientBuilder
            .defaultSystem("请使用中文回答所有问题。")
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(chatMemory),
                new SimpleLoggerAdvisor()
            )
            .build();
    }

    public Flux<String> streamChat(String sessionId, String message) {
        return streamChat(sessionId, message, false);
    }

    public Flux<String> streamChat(String sessionId, String message, boolean enableSearch) {
        var prompt = this.chatClient.prompt();
        
        if (enableSearch) {
            // 先执行搜索，把结果作为上下文
            String searchResult = exaSearch.apply(new ExaSearchRequest(message));
            String userMessageWithContext = """
                请基于以下搜索结果回答用户问题。
                
                搜索结果：
                %s
                
                用户问题：%s
                """.formatted(searchResult, message);
            prompt.user(userMessageWithContext);
            
            // 先输出搜索过程（用特殊标记包裹，前端可折叠），再输出 AI 回答
            String searchProcessBlock = """
                ```search-process
                🔍 联网搜索完成
                
                %s
                ```
                
                ---
                
                """.formatted(searchResult);
            
            Flux<String> searchFlux = Flux.just(searchProcessBlock);
            Flux<String> aiFlux = prompt
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .stream()
                .content();
            
            return searchFlux.concatWith(aiFlux);
        } else {
            prompt.user(message);
        }
        
        return prompt
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
