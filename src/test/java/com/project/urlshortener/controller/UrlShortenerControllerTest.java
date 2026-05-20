package com.project.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.urlshortener.dto.UrlShortenRequest;
import com.project.urlshortener.dto.UrlShortenResponse;
import com.project.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.project.urlshortener.config.RateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;

@WebMvcTest(UrlShortenerController.class)
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService service;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void shortenUrl_Success() throws Exception {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("https://example.com");

        UrlShortenResponse response = UrlShortenResponse.builder()
                .originalUrl("https://example.com")
                .shortUrl("http://localhost:8080/ABC123")
                .createdAt(LocalDateTime.now())
                .build();

        when(service.shortenUrl(eq("https://example.com"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/ABC123"));
    }

    @Test
    void shortenUrl_InvalidUrl() throws Exception {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("not-a-url");

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_Success() throws Exception {
        String shortCode = "ABC123";
        String originalUrl = "https://example.com";

        when(service.resolveUrl(shortCode)).thenReturn(originalUrl);
        
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));

        verify(valueOps).increment("clicks:" + shortCode, 1);
        verify(setOps).add("sync_click_keys", shortCode);
    }
}
