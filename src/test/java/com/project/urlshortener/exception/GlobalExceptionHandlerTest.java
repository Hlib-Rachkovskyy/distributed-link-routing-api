package com.project.urlshortener.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.urlshortener.controller.UrlShortenerController;
import com.project.urlshortener.dto.UrlShortenRequest;
import com.project.urlshortener.service.UrlShortenerService;
import com.project.urlshortener.config.RateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlShortenerController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlShortenerService service;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplateObject;

    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void handleUrlNotFoundException() throws Exception {
        String shortCode = "NOTFOUND";
        when(service.resolveUrl(shortCode)).thenThrow(new UrlNotFoundException("Short URL not found"));

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Short URL not found"));
    }

    @Test
    void handleValidationExceptions() throws Exception {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("invalid-url-format");

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.originalUrl").value("Original URL must be a valid URL"));
    }

    @Test
    void handleGenericException() throws Exception {
        String shortCode = "ERROR";
        when(service.resolveUrl(shortCode)).thenThrow(new RuntimeException("Something went wrong"));

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
