package com.nem12.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Test
    void shouldAddRequestIdToResponseHeaderAndMdc() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();

        doAnswer(invocation -> {
            // During filter chain execution, MDC should have requestId
            String requestId = MDC.get("requestId");
            assertNotNull(requestId);
            assertEquals(8, requestId.length());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).addHeader(eq("X-Request-Id"), argThat(id -> id.length() == 8));
        verify(filterChain).doFilter(request, response);

        // MDC should be cleaned up after filter completes
        assertNull(MDC.get("requestId"));
    }
}
