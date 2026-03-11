package com.chat.filter;

import com.chat.api.Result;
import com.chat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class LoginConcurrencyFilter extends OncePerRequestFilter {

    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;

    public LoginConcurrencyFilter(int limit, ObjectMapper objectMapper) {
        this.semaphore = new Semaphore(limit);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!semaphore.tryAcquire()) {
            writeTooManyRequestsResponse(response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            semaphore.release();
        }
    }

    private void writeTooManyRequestsResponse(HttpServletResponse
                                                      response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "1");

        Result<?> body = Result.builder()
                .status(ErrorCode.SERVER_BUSY.getStatus())
                .message(ErrorCode.SERVER_BUSY.getErrorMessage())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
