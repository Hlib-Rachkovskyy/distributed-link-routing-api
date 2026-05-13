package com.project.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import lombok.Data;

@Data
public class UrlShortenRequest {
    @NotBlank(message = "Original URL cannot be blank")
    @URL(message = "Original URL must be a valid URL")
    private String originalUrl;
}
