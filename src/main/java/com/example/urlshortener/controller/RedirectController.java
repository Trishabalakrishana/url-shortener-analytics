package com.example.urlshortener.controller;

import com.example.urlshortener.service.ClickService;
import com.example.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final ClickService clickService;

    public RedirectController(LinkService linkService, ClickService clickService) {
        this.linkService = linkService;
        this.clickService = clickService;
    }

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Optional<LinkService.ResolvedLink> resolved = linkService.resolve(shortCode);

        if (resolved.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Fire-and-forget: the redirect response goes out immediately;
        // the click write happens async so it never adds latency here,
        // and it needs no extra DB round-trip since resolve() already gave us the id.
        clickService.recordClickAsync(
                resolved.get().id(),
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
        );

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(resolved.get().longUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .build();
    }
}
