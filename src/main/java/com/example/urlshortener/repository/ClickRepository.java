package com.example.urlshortener.repository;

import com.example.urlshortener.model.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickRepository extends JpaRepository<Click, Long> {

    long countByLinkId(Long linkId);

    @Query(value = """
            SELECT CAST(clicked_at AS date) AS day, COUNT(*) AS clicks
            FROM click
            WHERE link_id = :linkId
            GROUP BY CAST(clicked_at AS date)
            ORDER BY day DESC
            LIMIT 30
            """, nativeQuery = true)
    List<Object[]> countClicksPerDay(@Param("linkId") Long linkId);

    @Query(value = """
            SELECT COALESCE(NULLIF(referrer, ''), 'direct') AS ref, COUNT(*) AS clicks
            FROM click
            WHERE link_id = :linkId
            GROUP BY COALESCE(NULLIF(referrer, ''), 'direct')
            ORDER BY clicks DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> topReferrers(@Param("linkId") Long linkId);
}
