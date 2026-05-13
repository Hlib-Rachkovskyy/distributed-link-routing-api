package com.project.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlShortenResponse {
    private String originalUrl;
    private String shortUrl;
    private LocalDateTime createdAt;
}
