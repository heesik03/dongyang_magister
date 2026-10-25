package com.magister.backend.global.security;

import com.magister.backend.global.error.code.FrameworkErrorCode;
import com.magister.backend.global.response.ApiResult;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final ObjectMapper json;

    public RequestSizeFilter(ObjectMapper json) {
        this.json = json;
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
                        var input = new ByteArrayInputStream(body);
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
                                    if (!isFinished()) listener.onDataAvailable();
                                    if (isFinished()) listener.onAllDataRead();
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
        response.setStatus(413);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(
                response.getOutputStream(),
                ApiResult.failure(new FrameworkErrorCode(HttpStatus.PAYLOAD_TOO_LARGE)));
    }
}
