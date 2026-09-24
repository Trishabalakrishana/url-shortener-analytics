package com.example.urlshortener.service;

import com.example.urlshortener.dto.LinkDtos.CreateLinkRequest;
import com.example.urlshortener.dto.LinkDtos.LinkResponse;
import com.example.urlshortener.model.Link;
import com.example.urlshortener.repository.LinkRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class LinkService {

    private static final String CACHE_PREFIX = "link:";

    private final LinkRepository linkRepository;
    private final Base62Service base62Service;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;
    private final long cacheTtlSeconds;

    public LinkService(
            LinkRepository linkRepository,
            Base62Service base62Service,
            StringRedisTemplate redisTemplate,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.link-cache-ttl-seconds}") long cacheTtlSeconds
    ) {
        this.linkRepository = linkRepository;
        this.base62Service = base62Service;
        this.redisTemplate = redisTemplate;
        this.baseUrl = baseUrl;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public LinkResponse createLink(CreateLinkRequest request, Long ownerId) {
        String shortCode;
        boolean isCustom = request.customAlias() != null && !request.customAlias().isBlank();

        if (isCustom) {
            if (linkRepository.existsByShortCode(request.customAlias())) {
                throw new IllegalArgumentException("Alias already taken: " + request.customAlias());
            }
            shortCode = request.customAlias();

            Link link = Link.builder()
                    .shortCode(shortCode)
                    .longUrl(request.longUrl())
                    .customAlias(true)
                    .ownerId(ownerId)
                    .expiresAt(request.expiresAt())
                    .build();
            link = linkRepository.save(link);
            return toResponse(link);
        } else {
            // Counter-based: save first (without code) to get the auto-increment id,
            // then derive the short code from that id. Avoids collision checks entirely.
            Link link = Link.builder()
                    .shortCode("PENDING") // placeholder, replaced below; unique temp value not required
                    .longUrl(request.longUrl())
                    .customAlias(false)
                    .ownerId(ownerId)
                    .expiresAt(request.expiresAt())
                    .build();
            link = linkRepository.save(link);

            shortCode = base62Service.encode(link.getId());
            link.setShortCode(shortCode);
            link = linkRepository.save(link);

            return toResponse(link);
        }
    }

    /**
     * A resolved link's redirect target plus its id, so the hot redirect path
     * never needs a second DB lookup just to log a click.
     */
    public record ResolvedLink(Long id, String longUrl) {}

    /**
     * Resolves a short code, checking Redis first (format "id::longUrl").
     * On a cache miss, falls back to Postgres and repopulates the cache.
     * This is the only lookup the hot redirect path performs.
     */
    public Optional<ResolvedLink> resolve(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached.equals("__EXPIRED__")) return Optional.empty();
            String[] parts = cached.split("::", 2);
            return Optional.of(new ResolvedLink(Long.valueOf(parts[0]), parts[1]));
        }

        Optional<Link> linkOpt = linkRepository.findByShortCode(shortCode);
        if (linkOpt.isEmpty()) {
            return Optional.empty();
        }

        Link link = linkOpt.get();
        if (link.isExpired()) {
            redisTemplate.opsForValue().set(cacheKey, "__EXPIRED__", Duration.ofSeconds(cacheTtlSeconds));
            return Optional.empty();
        }

        redisTemplate.opsForValue().set(
                cacheKey, link.getId() + "::" + link.getLongUrl(), Duration.ofSeconds(cacheTtlSeconds));
        return Optional.of(new ResolvedLink(link.getId(), link.getLongUrl()));
    }

    public Link getByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new EntityNotFoundException("Link not found: " + shortCode));
    }

    private LinkResponse toResponse(Link link) {
        return new LinkResponse(
                link.getId(),
                link.getShortCode(),
                baseUrl + "/r/" + link.getShortCode(),
                link.getLongUrl(),
                link.getCreatedAt(),
                link.getExpiresAt()
        );
    }
}
