package com.project.urlshortener.service;

import com.project.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsSyncService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UrlMappingRepository repository;

    // Run every 10 minutes (600,000 ms)
    @Scheduled(fixedDelay = 600000)
    @Transactional
    public void syncClicksToDatabase() {
        log.info("Starting scheduled analytics sync...");

        // Pop keys that need syncing
        String shortCode;
        int syncedCount = 0;

        while ((shortCode = stringRedisTemplate.opsForSet().pop("sync_click_keys")) != null) {
            String countKey = "clicks:" + shortCode;
            
            // Get the current value and atomically reset it to 0 to not lose concurrent clicks
            String clicksStr = stringRedisTemplate.opsForValue().getAndSet(countKey, "0");
            
            if (clicksStr != null) {
                long clicks = Long.parseLong(clicksStr);
                if (clicks > 0) {
                    repository.incrementClickCount(shortCode, clicks);
                    syncedCount++;
                }
            }
        }
        
        log.info("Finished analytics sync. Synced {} URLs.", syncedCount);
    }
}
