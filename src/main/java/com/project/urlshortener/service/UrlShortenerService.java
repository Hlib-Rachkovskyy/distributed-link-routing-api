package com.project.urlshortener.service;

import com.project.urlshortener.dto.UrlShortenResponse;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.model.UrlMapping;
import com.project.urlshortener.repository.UrlMappingRepository;
import com.project.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final Base62Encoder encoder;

    @Transactional
    public UrlShortenResponse shortenUrl(String originalUrl, String baseUrl) {
        String shortCode;
        do {
            shortCode = encoder.generateRandomShortCode(6);
        } while (repository.existsByShortCode(shortCode));

        UrlMapping mapping = UrlMapping.builder()
                .originalUrl(originalUrl)
                .shortCode(shortCode)
                .build();

        mapping = repository.save(mapping);

        return UrlShortenResponse.builder()
                .originalUrl(mapping.getOriginalUrl())
                .shortUrl(baseUrl + mapping.getShortCode())
                .createdAt(mapping.getCreatedAt())
                .build();
    }

    @Cacheable(value = "urls", key = "#shortCode")
    public String resolveUrl(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found"));
        return mapping.getOriginalUrl();
    }
}
