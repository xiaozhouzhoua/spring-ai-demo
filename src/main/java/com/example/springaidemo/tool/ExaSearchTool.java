package com.example.springaidemo.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
            // 限制搜索最近7天的内容
            String startDate = java.time.LocalDate.now().minusDays(7).toString();
            
            var requestBody = Map.of(
                "query", request.query(),
                "numResults", 10,
                "contents", Map.of("text", true),
                "startPublishedDate", startDate
            );

            var response = restClient.post()
                .uri("/search")
                .body(requestBody)
                .retrieve()
                .body(ExaSearchResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return "No results found for: " + request.query();
            }

            StringBuilder sb = new StringBuilder();
            for (var result : response.results()) {
                sb.append("Title: ").append(result.title()).append("\n");
                sb.append("URL: ").append(result.url()).append("\n");
                if (result.text() != null) {
                    String text = result.text();
                    if (text.length() > 500) text = text.substring(0, 500) + "...";
                    sb.append("Content: ").append(text).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        };
    }

    public record ExaSearchRequest(String query) {}
    record ExaSearchResponse(List<ExaResult> results) {}
    record ExaResult(String title, String url, String text) {}
}
