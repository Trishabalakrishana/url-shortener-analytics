package com.example.urlshortener.service;

import com.example.urlshortener.dto.LinkDtos.AnalyticsResponse;
import com.example.urlshortener.dto.LinkDtos.DailyClicks;
import com.example.urlshortener.dto.LinkDtos.ReferrerCount;
import com.example.urlshortener.model.Click;
import com.example.urlshortener.model.Link;
import com.example.urlshortener.repository.ClickRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.List;

@Service
public class ClickService {

    private final ClickRepository clickRepository;

    public ClickService(ClickRepository clickRepository) {
        this.clickRepository = clickRepository;
    }

    /**
     * Records a click off the hot redirect path. The redirect itself responds
     * immediately (served from Redis); this write happens on a separate thread
     * so analytics logging never adds latency to the 302 response.
     */
    @Async
    public void recordClickAsync(Long linkId, String referrer, String userAgent, String ip) {
        Click click = Click.builder()
                .linkId(linkId)
                .referrer(referrer)
                .userAgent(userAgent)
                .ipHash(hashIp(ip))
                .build();
        clickRepository.save(click);
    }

    public AnalyticsResponse getAnalytics(Link link) {
        long total = clickRepository.countByLinkId(link.getId());

        List<DailyClicks> perDay = clickRepository.countClicksPerDay(link.getId()).stream()
                .map(row -> new DailyClicks(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();

        List<ReferrerCount> topReferrers = clickRepository.topReferrers(link.getId()).stream()
                .map(row -> new ReferrerCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new AnalyticsResponse(link.getShortCode(), total, perDay, topReferrers);
    }

    private String hashIp(String ip) {
        if (ip == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
