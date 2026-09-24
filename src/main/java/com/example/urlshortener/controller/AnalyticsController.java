package com.example.urlshortener.controller;

import com.example.urlshortener.dto.LinkDtos.AnalyticsResponse;
import com.example.urlshortener.model.Link;
import com.example.urlshortener.service.ClickService;
import com.example.urlshortener.service.LinkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class AnalyticsController {

    private final LinkService linkService;
    private final ClickService clickService;

    public AnalyticsController(LinkService linkService, ClickService clickService) {
        this.linkService = linkService;
        this.clickService = clickService;
    }

    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse analytics(@PathVariable String shortCode) {
        Link link = linkService.getByShortCode(shortCode);
        return clickService.getAnalytics(link);
    }
}
