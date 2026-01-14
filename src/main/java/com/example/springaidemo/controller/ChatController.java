package com.example.springaidemo.controller;

import com.example.springaidemo.service.ChatService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // SSE 格式流式响应
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> streamChat(@RequestBody ChatRequest request) {
        Flux<String> stream = chatService.streamChat(
            request.sessionId(), 
            request.message(),
            request.enableSearch() != null && request.enableSearch()
        );
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .header("Connection", "keep-alive")
            .body(stream);
    }

    // JSON Stream 格式流式响应
    @PostMapping(value = "/stream/json", produces = "application/stream+json")
    public ResponseEntity<Flux<ChatChunk>> streamChatJson(@RequestBody ChatRequest request) {
        Flux<ChatChunk> stream = chatService.streamChat(
            request.sessionId(), 
            request.message(),
            request.enableSearch() != null && request.enableSearch()
        ).map(ChatChunk::new);
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(stream);
    }

    // 获取历史消息
    @GetMapping("/history/{sessionId}")
    public List<MessageDto> getHistory(@PathVariable String sessionId) {
        return chatService.getHistory(sessionId).stream()
            .map(m -> new MessageDto(m.getMessageType().name().toLowerCase(), m.getContent()))
            .toList();
    }

    // 获取所有会话列表
    @GetMapping("/sessions")
    public List<SessionDto> getSessions() {
        return chatService.getAllSessions();
    }

    // 清空会话
    @DeleteMapping("/history/{sessionId}")
    public void clearHistory(@PathVariable String sessionId) {
        chatService.clearHistory(sessionId);
    }

    // 修改会话标题
    @PutMapping("/sessions/{sessionId}/title")
    public void updateSessionTitle(@PathVariable String sessionId, @RequestBody TitleRequest request) {
        chatService.updateSessionTitle(sessionId, request.title());
    }

    public record ChatChunk(String content) {}
    public record ChatRequest(String sessionId, String message, Boolean enableSearch) {}
    public record MessageDto(String role, String content) {}
    public record SessionDto(String id, String title, long timestamp) {}
    public record TitleRequest(String title) {}
}
