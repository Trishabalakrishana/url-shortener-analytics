package com.example.urlshortener.controller;

import com.example.urlshortener.dto.LinkDtos.CreateLinkRequest;
import com.example.urlshortener.dto.LinkDtos.LinkResponse;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;
    private final UserRepository userRepository;

    public LinkController(LinkService linkService, UserRepository userRepository) {
        this.linkService = linkService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @Valid @RequestBody CreateLinkRequest request,
            Authentication authentication
    ) {
        Long ownerId = resolveOwnerId(authentication);
        LinkResponse response = linkService.createLink(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns null for anonymous requests (no JWT) so anonymous link creation
     * still works, matching a typical bit.ly-style flow.
     */
    private Long resolveOwnerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
