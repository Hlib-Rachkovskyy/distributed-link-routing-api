package com.project.urlshortener.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Object handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        handler = new Object();
    }

    @Test
    void preHandle_AllowRequest_WithinLimit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);

        request.setRemoteAddr("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, handler);

        assertTrue(result);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_FirstRequest_SetsExpiry() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);

        request.setRemoteAddr("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, handler);

        assertTrue(result);
        verify(redisTemplate, times(1)).expire(eq("rate_limit:127.0.0.1"), eq(1L), eq(TimeUnit.MINUTES));
    }

    @Test
    void preHandle_BlockRequest_ExceedLimit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(11L);

        request.setRemoteAddr("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, handler);

        assertFalse(result);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertEquals("Too many requests. Please try again later.", response.getContentAsString());
    }

    @Test
    void preHandle_UsesXForwardedForHeader() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(eq("rate_limit:203.0.113.195"), anyLong())).thenReturn(2L);

        request.addHeader("X-Forwarded-For", "203.0.113.195");
        request.setRemoteAddr("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, handler);

        assertTrue(result);
        verify(valueOperations, times(1)).increment(eq("rate_limit:203.0.113.195"), eq(1L));
    }
}
