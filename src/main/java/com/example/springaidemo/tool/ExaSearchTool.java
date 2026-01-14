package com.example.springaidemo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class ExaSearchTool {

    @Bean
    @Description("Search the web for current information using Exa AI. Use this when you need real-time or up-to-date information.")
    public Function<ExaSearchRequest, String> exaSearch(@Value("${exa.api-key:}") String apiKey) {
        RestClient restClient = RestClient.builder()
            .baseUrl("https://api.exa.ai")
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();

        return request -> {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", request.query());
            requestBody.put("numResults", 10);
            requestBody.put("contents", Map.of("text", true));
            // 不限制时间范围，让搜索引擎自己判断

            var response = restClient.post()
                .uri("/search")
                .body(requestBody)
                .retrieve()
                .body(ExaSearchResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return formatSearchResult("搜索完成", request.query(), 0, List.of(), List.of());
            }

            // 分类搜索结果
            var categorizedResults = categorizeResults(response.results());
            var categories = categorizedResults.keySet().stream().toList();
            
            return formatSearchResult("搜索完成", request.query(), response.results().size(), categories, response.results());
        };
    }
    
    private Map<String, List<ExaResult>> categorizeResults(List<ExaResult> results) {
        return results.stream().collect(Collectors.groupingBy(this::categorizeResult));
    }
    
    private String categorizeResult(ExaResult result) {
        String title = result.title().toLowerCase();
        String url = result.url().toLowerCase();
        String text = result.text() != null ? result.text().toLowerCase() : "";
        
        // 技术文档
        if (url.contains("docs.") || url.contains("documentation") || 
            title.contains("文档") || title.contains("api") || title.contains("guide")) {
            return "技术文档";
        }
        
        // 新闻资讯
        if (url.contains("news") || url.contains("blog") || 
            title.contains("新闻") || title.contains("资讯") || title.contains("发布")) {
            return "新闻资讯";
        }
        
        // 教程指南
        if (title.contains("教程") || title.contains("tutorial") || 
            title.contains("如何") || title.contains("how to")) {
            return "教程指南";
        }
        
        // 问答社区
        if (url.contains("stackoverflow") || url.contains("zhihu") || 
            url.contains("csdn") || title.contains("问答")) {
            return "问答社区";
        }
        
        // 官方网站
        if (url.contains("github.com") || url.contains("官网") || 
            title.contains("官方") || title.contains("official")) {
            return "官方网站";
        }
        
        return "其他资源";
    }
    
    private String formatSearchResult(String status, String query, int totalResults, 
                                    List<String> categories, List<ExaResult> results) {
        // 使用JSON格式，前端更容易解析
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"search\",");
        sb.append("\"query\":\"").append(escapeJson(query)).append("\",");
        sb.append("\"time\":\"").append(java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\",");
        sb.append("\"total\":").append(totalResults).append(",");
        sb.append("\"status\":\"").append(status).append("\",");
        sb.append("\"categories\":[");
        
        if (!results.isEmpty()) {
            var categorizedResults = categorizeResults(results);
            boolean firstCat = true;
            
            for (var entry : categorizedResults.entrySet()) {
                if (!firstCat) sb.append(",");
                firstCat = false;
                
                String category = entry.getKey();
                List<ExaResult> categoryResults = entry.getValue();
                
                sb.append("{\"name\":\"").append(escapeJson(category)).append("\",");
                sb.append("\"items\":[");
                
                for (int i = 0; i < categoryResults.size(); i++) {
                    if (i > 0) sb.append(",");
                    var result = categoryResults.get(i);
                    sb.append("{\"title\":\"").append(escapeJson(result.title())).append("\",");
                    sb.append("\"url\":\"").append(escapeJson(result.url())).append("\",");
                    String snippet = result.text() != null ? result.text() : "";
                    if (snippet.length() > 150) snippet = snippet.substring(0, 150) + "...";
                    sb.append("\"snippet\":\"").append(escapeJson(snippet)).append("\"}");
                }
                sb.append("]}");
            }
        }
        
        sb.append("]}");
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record ExaSearchRequest(String query) {}
    record ExaSearchResponse(List<ExaResult> results) {}
    record ExaResult(String title, String url, String text) {}
}
