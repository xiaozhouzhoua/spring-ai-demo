package com.example.springaidemo.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileChatMemory implements ChatMemory {

    private final Path chatDir;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();
    private final Map<String, String> titleCache = new ConcurrentHashMap<>();
    private final Path titlesFile;

    public FileChatMemory(String chatDirPath) {
        this.chatDir = Paths.get(chatDirPath);
        this.titlesFile = chatDir.resolve("_titles.json");
        this.objectMapper = new ObjectMapper();
        try {
            Files.createDirectories(chatDir);
            loadTitles();
        } catch (IOException e) {
            throw new RuntimeException("无法创建聊天目录: " + chatDir, e);
        }
    }

    private void loadTitles() {
        if (Files.exists(titlesFile)) {
            try {
                Map<String, String> titles = objectMapper.readValue(titlesFile.toFile(), new TypeReference<>() {});
                titleCache.putAll(titles);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void saveTitles() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(titlesFile.toFile(), titleCache);
        } catch (IOException e) {
            // ignore
        }
    }

    public void updateTitle(String sessionId, String title) {
        titleCache.put(sessionId, title);
        saveTitles();
    }

    public String getTitle(String sessionId) {
        return titleCache.get(sessionId);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> existing = cache.computeIfAbsent(conversationId, this::loadFromFile);
        existing.addAll(messages);
        saveToFile(conversationId, existing);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> messages = cache.computeIfAbsent(conversationId, this::loadFromFile);
        if (lastN <= 0 || messages.size() <= lastN) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - lastN, messages.size()));
    }

    @Override
    public void clear(String conversationId) {
        cache.remove(conversationId);
        titleCache.remove(conversationId);
        saveTitles();
        try {
            Files.deleteIfExists(getFilePath(conversationId));
        } catch (IOException e) {
            // ignore
        }
    }

    // 获取所有会话ID和基本信息
    public List<SessionInfo> getAllSessions() {
        List<SessionInfo> sessions = new ArrayList<>();
        try (var stream = Files.list(chatDir)) {
            stream.filter(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("_titles.json"))
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String sessionId = filename.substring(0, filename.length() - 5);
                    try {
                        long timestamp = Files.getLastModifiedTime(path).toMillis();
                        // 优先使用自定义标题，否则获取第一条用户消息作为标题
                        String customTitle = titleCache.get(sessionId);
                        String title;
                        if (customTitle != null && !customTitle.isBlank()) {
                            title = customTitle;
                        } else {
                            List<Message> msgs = cache.computeIfAbsent(sessionId, this::loadFromFile);
                            title = msgs.stream()
                                .filter(m -> m.getMessageType().name().equals("USER"))
                                .findFirst()
                                .map(m -> {
                                    String content = m.getContent();
                                    // 如果是联网搜索的消息，提取真正的用户问题
                                    if (content.contains("用户问题：")) {
                                        int idx = content.lastIndexOf("用户问题：");
                                        content = content.substring(idx + 5).trim();
                                    }
                                    return content.length() > 30 ? content.substring(0, 30) + "..." : content;
                                })
                                .orElse("新对话");
                        }
                        sessions.add(new SessionInfo(sessionId, title, timestamp));
                    } catch (IOException e) {
                        // ignore
                    }
                });
        } catch (IOException e) {
            // ignore
        }
        // 按时间倒序
        sessions.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return sessions;
    }

    public record SessionInfo(String id, String title, long timestamp) {}

    private Path getFilePath(String conversationId) {
        return chatDir.resolve(conversationId + ".json");
    }

    private List<Message> loadFromFile(String conversationId) {
        Path file = getFilePath(conversationId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<MessageData> dataList = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            List<Message> messages = new ArrayList<>();
            for (MessageData data : dataList) {
                messages.add(data.toMessage());
            }
            return messages;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void saveToFile(String conversationId, List<Message> messages) {
        try {
            List<MessageData> dataList = messages.stream().map(MessageData::from).toList();
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(getFilePath(conversationId).toFile(), dataList);
        } catch (IOException e) {
            throw new RuntimeException("保存聊天记录失败", e);
        }
    }

    // 用于 JSON 序列化的数据类
    record MessageData(String type, String content) {
        static MessageData from(Message message) {
            return new MessageData(message.getMessageType().name(), message.getContent());
        }

        Message toMessage() {
            return switch (type) {
                case "USER" -> new UserMessage(content);
                case "ASSISTANT" -> new AssistantMessage(content);
                case "SYSTEM" -> new SystemMessage(content);
                default -> new UserMessage(content);
            };
        }
    }
}
