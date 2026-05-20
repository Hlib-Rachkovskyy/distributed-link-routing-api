package com.project.urlshortener.service;

import com.project.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsSyncServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UrlMappingRepository repository;

    @InjectMocks
    private AnalyticsSyncService syncService;

    @Test
    @SuppressWarnings("unchecked")
    void syncClicksToDatabase_Success() {
        String shortCode = "ABC123";
        String clicks = "5";
        
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        when(setOps.pop("sync_click_keys")).thenReturn(shortCode, (String) null);
        when(valueOps.getAndSet("clicks:" + shortCode, "0")).thenReturn(clicks);

        syncService.syncClicksToDatabase();

        verify(repository).incrementClickCount(shortCode, 5L);
        verify(setOps, times(2)).pop("sync_click_keys");
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncClicksToDatabase_NoKeys() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.pop("sync_click_keys")).thenReturn(null);

        syncService.syncClicksToDatabase();

        verifyNoInteractions(repository);
    }
}
