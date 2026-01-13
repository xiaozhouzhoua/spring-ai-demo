package com.example.springaidemo.controller;

import com.example.springaidemo.model.SearchResult;
import com.example.springaidemo.service.SearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResult search(@RequestParam String q) {
        return searchService.search(q);
    }
}
