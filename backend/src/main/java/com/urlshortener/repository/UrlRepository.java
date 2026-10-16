package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    List<Url> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Url> findByIdAndOwnerId(Long id, Long ownerId);
}
