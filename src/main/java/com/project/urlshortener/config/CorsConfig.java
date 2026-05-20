package com.project.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Apply to all endpoints
                .allowedOrigins("https://distributed-link-routing-frontend.vercel.app") // Your Vercel URL
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Explicitly allow OPTIONS for preflight
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}