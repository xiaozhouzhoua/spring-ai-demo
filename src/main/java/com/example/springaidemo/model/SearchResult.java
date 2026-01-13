package com.example.springaidemo.model;

import java.util.List;

public record SearchResult(
    String answer,
    List<Source> sources
) {
    public record Source(String title, String url) {}
}
