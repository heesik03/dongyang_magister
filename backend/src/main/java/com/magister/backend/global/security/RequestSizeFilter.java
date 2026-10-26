package com.magister.backend.global.security;

import com.magister.backend.global.error.code.FrameworkErrorCode;
import com.magister.backend.global.response.HttpResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final ObjectMapper objectMapper;

    public RequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")
                || (!request.getMethod().equals("POST") && !request.getMethod().equals("PUT"))) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            reject(response);
            return;
        }
        // chunked 전송도 같은 상한을 적용해 JSON 역직렬화 전에 크기를 제한한다.
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            reject(response);
            return;
        }
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public ServletInputStream getInputStream() {
                        ByteArrayInputStream input = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            @Override
                            public int read() {
                                return input.read();
                            }

                            @Override
                            public int read(byte[] target, int offset, int length) {
                                return input.read(target, offset, length);
                            }

                            @Override
                            public boolean isFinished() {
                                return input.available() == 0;
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setReadListener(ReadListener listener) {
                                try {
                                    if (!isFinished()) {
                                        listener.onDataAvailable();
                                    }
                                    if (isFinished()) {
                                        listener.onAllDataRead();
                                    }
                                } catch (IOException error) {
                                    listener.onError(error);
                                }
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(
                                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                    }
                },
                response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        HttpResponseWriter.failure(
                response, objectMapper, new FrameworkErrorCode(HttpStatus.CONTENT_TOO_LARGE));
    }
}
