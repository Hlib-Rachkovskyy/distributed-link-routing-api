package com.project.urlshortener.service;

import com.project.urlshortener.dto.UrlShortenResponse;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.model.UrlMapping;
import com.project.urlshortener.repository.UrlMappingRepository;
import com.project.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private Base62Encoder encoder;

    @InjectMocks
    private UrlShortenerService service;

    @Test
    void shortenUrl_Success() {
        String originalUrl = "https://example.com";
        String baseUrl = "http://localhost:8080/";
        String shortCode = "ABC123";
        LocalDateTime now = LocalDateTime.now();

        when(encoder.generateRandomShortCode(6)).thenReturn(shortCode);
        when(repository.existsByShortCode(shortCode)).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setCreatedAt(now);
            return mapping;
        });

        UrlShortenResponse response = service.shortenUrl(originalUrl, baseUrl);

        assertEquals(originalUrl, response.getOriginalUrl());
        assertEquals(baseUrl + shortCode, response.getShortUrl());
        assertEquals(now, response.getCreatedAt());
        verify(repository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    void shortenUrl_WithCollision() {
        String originalUrl = "https://example.com";
        String baseUrl = "http://localhost:8080/";
        String collisionCode = "OLD123";
        String newCode = "NEW123";

        when(encoder.generateRandomShortCode(6)).thenReturn(collisionCode, newCode);
        when(repository.existsByShortCode(collisionCode)).thenReturn(true);
        when(repository.existsByShortCode(newCode)).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlShortenResponse response = service.shortenUrl(originalUrl, baseUrl);

        assertEquals(baseUrl + newCode, response.getShortUrl());
        verify(encoder, times(2)).generateRandomShortCode(6);
    }

    @Test
    void resolveUrl_Success() {
        String shortCode = "ABC123";
        String originalUrl = "https://example.com";
        UrlMapping mapping = UrlMapping.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .build();

        when(repository.findByShortCode(shortCode)).thenReturn(Optional.of(mapping));

        String result = service.resolveUrl(shortCode);

        assertEquals(originalUrl, result);
    }

    @Test
    void resolveUrl_NotFound() {
        String shortCode = "NOTFOUND";
        when(repository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> service.resolveUrl(shortCode));
    }
}
