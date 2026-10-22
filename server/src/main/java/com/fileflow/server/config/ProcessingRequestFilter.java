package com.fileflow.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Admit work before multipart parsing, PDF validation, table extraction, or workbook allocation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ProcessingRequestFilter extends OncePerRequestFilter {
    private static final long JSON_LIMIT = 8L * 1024 * 1024;
    private static final long WINDOW_MILLIS = 60_000;
    private final Semaphore slots;
    private final int requestsPerMinute;
    private final Map<String, Window> clients = new HashMap<>();

    public ProcessingRequestFilter(ProcessingProperties properties,
            @Value("${fileflow.processing.requests-per-minute:30}") int requestsPerMinute) {
        slots = new Semaphore(Math.max(1, properties.concurrency()), true);
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Do not trust arbitrary forwarded client-IP headers. Configure trusted proxy handling at deployment.
        if (!allow(request.getRemoteAddr(), System.currentTimeMillis())) {
            reject(response, 429, "Too many processing requests. Wait one minute and try again.");
            return;
        }
        if (!slots.tryAcquire()) {
            reject(response, 503, "The processing service is busy. Try again shortly.");
            return;
        }
        try {
            boolean json = request.getContentType() != null && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json");
            if (json && request.getContentLengthLong() > JSON_LIMIT) {
                reject(response, 413, "The reviewed table request exceeds the 8 MB limit.");
                return;
            }
            chain.doFilter(json ? bounded(request) : request, response);
        } finally { slots.release(); }
    }

    private synchronized boolean allow(String client, long now) {
        Window window = clients.get(client);
        if (window == null || now - window.started >= WINDOW_MILLIS) {
            if (clients.size() >= 4096) clients.entrySet().removeIf(entry -> now - entry.getValue().started >= WINDOW_MILLIS);
            if (!clients.containsKey(client) && clients.size() >= 4096) return false;
            clients.put(client, new Window(now));
            return true;
        }
        return ++window.count <= requestsPerMinute;
    }

    private static HttpServletRequest bounded(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() throws IOException {
                ServletInputStream source = super.getInputStream();
                return new ServletInputStream() {
                    private long bytes;
                    private int counted(int read) throws IOException {
                        if (read > 0 && (bytes += read) > JSON_LIMIT) throw new IOException("The JSON request exceeds the safe input limit.");
                        return read;
                    }
                    @Override public int read() throws IOException {
                        int value = source.read();
                        if (value >= 0) counted(1);
                        return value;
                    }
                    @Override public int read(byte[] data, int offset, int length) throws IOException { return counted(source.read(data, offset, length)); }
                    @Override public boolean isFinished() { return source.isFinished(); }
                    @Override public boolean isReady() { return source.isReady(); }
                    @Override public void setReadListener(ReadListener listener) { source.setReadListener(listener); }
                };
            }
        };
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (status == 429 || status == 503) response.setHeader("Retry-After", status == 429 ? "60" : "5");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
    }

    private static final class Window {
        final long started;
        int count = 1;
        Window(long started) { this.started = started; }
    }
}
