package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class LinkDtos {

    public record CreateLinkRequest(
            @NotBlank(message = "longUrl is required")
            @Pattern(regexp = "^https?://.+", message = "longUrl must start with http:// or https://")
            String longUrl,

            @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "alias must be 3-32 chars, alphanumeric/-/_")
            String customAlias, // optional, may be null

            Instant expiresAt // optional, may be null
    ) {}

    public record LinkResponse(
            Long id,
            String shortCode,
            String shortUrl,
            String longUrl,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record DailyClicks(String day, long clicks) {}

    public record ReferrerCount(String referrer, long clicks) {}

    public record AnalyticsResponse(
            String shortCode,
            long totalClicks,
            java.util.List<DailyClicks> clicksPerDay,
            java.util.List<ReferrerCount> topReferrers
    ) {}
}
