package com.example.urlshortener.repository;

import com.example.urlshortener.model.Link;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    List<Link> findByOwnerId(Long ownerId);
}
