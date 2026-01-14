package com.example.springaidemo.model;

import java.util.List;

public record SearchResult(
    String answer,
    List<Source> sources,
    SearchSummary summary
) {
    public record Source(String title, String url, String category, String snippet) {}
    
    public record SearchSummary(
        int totalResults,
        String query,
        List<String> categories,
        String status
    ) {}
}
